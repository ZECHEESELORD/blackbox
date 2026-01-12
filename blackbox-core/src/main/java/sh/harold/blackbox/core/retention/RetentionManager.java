package sh.harold.blackbox.core.retention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Enforces on-disk retention for incident bundles.
 */
public final class RetentionManager {
    private static final DateTimeFormatter INCIDENT_TIMESTAMP = new DateTimeFormatterBuilder()
        .appendPattern("yyyyMMdd-HHmmss.SSS")
        .appendOffset("+HHmm", "Z")
        .toFormatter(Locale.ROOT);

    private final Clock clock;
    private final System.Logger logger;
    private final FileDeleter deleter;

    public RetentionManager(Clock clock, System.Logger logger, FileDeleter deleter) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    public RetentionStats enforce(Path incidentDir, RetentionPolicy policy) {
        Objects.requireNonNull(incidentDir, "incidentDir");
        Objects.requireNonNull(policy, "policy");

        if (!Files.exists(incidentDir)) {
            return new RetentionStats(0, 0, 0L, 0, 0L, 0);
        }

        List<IncidentFile> incidents = new ArrayList<>();
        List<Path> candidates;
        try (Stream<Path> stream = Files.list(incidentDir)) {
            candidates = stream
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .toList();
        } catch (IOException e) {
            logger.log(System.Logger.Level.WARNING, "Failed to list incident directory " + incidentDir, e);
            return new RetentionStats(0, 0, 0L, 0, 0L, 0);
        }

        int scanned = candidates.size();
        for (Path path : candidates) {
            try {
                Instant createdAt = resolveCreatedAt(path);
                long size = Files.size(path);
                incidents.add(new IncidentFile(path, createdAt, size));
            } catch (IOException e) {
                logger.log(System.Logger.Level.WARNING,
                    "Failed to read incident bundle metadata for " + path, e);
            }
        }
        incidents.sort(Comparator.comparing(IncidentFile::createdAt)
            .thenComparing(file -> file.path().getFileName().toString()));

        int deleted = 0;
        long bytesDeleted = 0;
        int deleteFailures = 0;

        long currentBytes = incidents.stream().mapToLong(IncidentFile::size).sum();
        int currentCount = incidents.size();

        if (incidents.isEmpty()) {
            long finalBytes = recalcBytes(incidentDir);
            int finalCount = recalcCount(incidentDir);
            return new RetentionStats(scanned, 0, 0L, 0, finalBytes, finalCount);
        }

        IncidentFile newest = incidents.get(incidents.size() - 1);
        Set<Path> failed = new HashSet<>();

        if (policy.maxAge() != null) {
            Instant cutoff = clock.instant().minus(policy.maxAge());
            for (IncidentFile incident : new ArrayList<>(incidents)) {
                if (incident.equals(newest)) {
                    continue;
                }
                if (incident.createdAt().isBefore(cutoff)) {
                    if (attemptDelete(incident, newest, failed)) {
                        incidents.remove(incident);
                        deleted++;
                        bytesDeleted += incident.size();
                        currentBytes -= incident.size();
                        currentCount--;
                    } else {
                        deleteFailures++;
                    }
                }
            }
            if (currentCount == 1 && newest.createdAt().isBefore(cutoff)) {
                logger.log(System.Logger.Level.WARNING,
                    "Retention maxAge exceeded but newest incident must be kept.");
            }
        }

        if (policy.maxCount() > 0) {
            while (currentCount > policy.maxCount()) {
                IncidentFile candidate = oldestCandidate(incidents, newest, failed);
                if (candidate == null) {
                    logger.log(System.Logger.Level.WARNING,
                        "Retention maxCount exceeded but no deletable incidents remain.");
                    break;
                }
                if (attemptDelete(candidate, newest, failed)) {
                    incidents.remove(candidate);
                    deleted++;
                    bytesDeleted += candidate.size();
                    currentBytes -= candidate.size();
                    currentCount--;
                } else {
                    deleteFailures++;
                }
                if (currentCount <= 1) {
                    logger.log(System.Logger.Level.WARNING,
                        "Retention would remove newest incident; stopping deletions.");
                    break;
                }
            }
        }

        if (policy.maxTotalBytes() > 0) {
            while (currentBytes > policy.maxTotalBytes()) {
                IncidentFile candidate = oldestCandidate(incidents, newest, failed);
                if (candidate == null) {
                    logger.log(System.Logger.Level.WARNING,
                        "Retention maxTotalBytes exceeded but no deletable incidents remain.");
                    break;
                }
                if (attemptDelete(candidate, newest, failed)) {
                    incidents.remove(candidate);
                    deleted++;
                    bytesDeleted += candidate.size();
                    currentBytes -= candidate.size();
                    currentCount--;
                } else {
                    deleteFailures++;
                }
                if (currentCount <= 1) {
                    logger.log(System.Logger.Level.WARNING,
                        "Retention would remove newest incident; stopping deletions.");
                    break;
                }
            }
        }

        long finalBytes = recalcBytes(incidentDir);
        int finalCount = recalcCount(incidentDir);

        return new RetentionStats(scanned, deleted, bytesDeleted, deleteFailures, finalBytes, finalCount);
    }

    private boolean attemptDelete(IncidentFile incident, IncidentFile newest, Set<Path> failed) {
        if (incident.equals(newest)) {
            return false;
        }
        if (failed.contains(incident.path())) {
            return false;
        }
        try {
            deleter.delete(incident.path());
            return true;
        } catch (IOException e) {
            failed.add(incident.path());
            logger.log(System.Logger.Level.WARNING, "Failed to delete incident " + incident.path(), e);
            return false;
        }
    }

    private static IncidentFile oldestCandidate(List<IncidentFile> incidents, IncidentFile newest, Set<Path> failed) {
        for (IncidentFile incident : incidents) {
            if (incident.equals(newest)) {
                continue;
            }
            if (failed.contains(incident.path())) {
                continue;
            }
            return incident;
        }
        return null;
    }

    private static long recalcBytes(Path incidentDir) {
        try (Stream<Path> stream = Files.list(incidentDir)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static int recalcCount(Path incidentDir) {
        try (Stream<Path> stream = Files.list(incidentDir)) {
            return (int) stream
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static Instant resolveCreatedAt(Path path) throws IOException {
        Instant fromId = parseCreatedAtFromName(path.getFileName().toString());
        if (fromId != null) {
            return fromId;
        }
        return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
    }

    private static Instant parseCreatedAtFromName(String fileName) {
        if (!fileName.endsWith(".zip")) {
            return null;
        }
        String baseName = fileName.substring(0, fileName.length() - ".zip".length());
        if (baseName.startsWith("incident-")) {
            baseName = baseName.substring("incident-".length());
        }
        int lastDash = baseName.lastIndexOf('-');
        if (lastDash <= 0) {
            return null;
        }
        String timestamp = baseName.substring(0, lastDash);
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(timestamp, INCIDENT_TIMESTAMP);
            return parsed.toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private record IncidentFile(Path path, Instant createdAt, long size) {
    }
}
