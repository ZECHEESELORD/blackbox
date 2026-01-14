package sh.harold.blackbox.hytale;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class BlackboxCommand extends CommandBase {
    private static final DateTimeFormatter INCIDENT_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSSZ").withLocale(Locale.ROOT);

    private final BlackboxRuntime runtime;

    BlackboxCommand(BlackboxRuntime runtime) {
        super("blackbox", "Blackbox incident recorder");
        this.runtime = Objects.requireNonNull(runtime, "runtime");

        addSubCommand(new DumpCommand(runtime));
        addSubCommand(new StatusCommand(runtime));
        addSubCommand(new ListCommand(runtime));
        addSubCommand(new OpenCommand(runtime));
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(Message.raw("Usage: /blackbox dump|status|list|open"));
    }

    private abstract static class RuntimeAsyncCommand extends AbstractAsyncCommand {
        protected final BlackboxRuntime runtime;

        protected RuntimeAsyncCommand(String name, String description, BlackboxRuntime runtime) {
            super(name, description);
            this.runtime = Objects.requireNonNull(runtime, "runtime");
        }

        protected final Executor executor() {
            return runtime.worker();
        }
    }

    private static final class DumpCommand extends RuntimeAsyncCommand {
        private DumpCommand(BlackboxRuntime runtime) {
            super("dump", "Trigger a manual incident capture", runtime);
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            return runAsync(context, () -> {
                Optional<String> id = runtime.captureManual();
                if (id.isEmpty()) {
                    context.sendMessage(Message.raw("Capture skipped or failed (cooldown/debounce or error)."));
                    return;
                }
                Path zip = runtime.incidentDir().resolve("incident-" + id.get() + ".zip");
                context.sendMessage(Message.raw("Captured incident " + id.get()));
                context.sendMessage(Message.raw("Bundle: " + zip));
            }, executor());
        }
    }

    private static final class StatusCommand extends RuntimeAsyncCommand {
        private StatusCommand(BlackboxRuntime runtime) {
            super("status", "Show Blackbox status", runtime);
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            return runAsync(context, () -> {
                Path incidentDir = runtime.incidentDir();

                int count = countIncidents(incidentDir);
                Optional<IncidentFile> latest = findLatestIncident(incidentDir);

                context.sendMessage(Message.raw("Blackbox status"));
                context.sendMessage(Message.raw("Config: " + runtime.configPath()));
                context.sendMessage(Message.raw("Incidents: " + incidentDir + " (" + count + ")"));
                if (latest.isPresent()) {
                    IncidentFile file = latest.get();
                    String when = file.createdAt().map(Instant::toString).orElse("unknown");
                    context.sendMessage(Message.raw("Last incident: " + file.id() + " @ " + when));
                } else {
                    context.sendMessage(Message.raw("Last incident: none"));
                }

                var retention = runtime.config().capturePolicy().retention();
                context.sendMessage(Message.raw("Retention: maxCount=" + retention.maxCount()
                    + ", maxTotalBytes=" + retention.maxTotalBytes()
                    + ", maxAge=" + (retention.maxAge() == null ? "none" : retention.maxAge())));

                var triggers = runtime.config().triggerPolicy();
                context.sendMessage(Message.raw("Triggers: cooldown=" + triggers.cooldown()
                    + ", debounce=" + triggers.debounce()
                    + ", stallDegradedMs=" + triggers.stallDegradedMs()
                    + ", stallCriticalMs=" + triggers.stallCriticalMs()));

                boolean discordEnabled = !runtime.config().discordWebhook().webhookUrl().isBlank();
                context.sendMessage(Message.raw("Discord webhook: " + (discordEnabled ? "enabled" : "disabled")));
                context.sendMessage(Message.raw("Web UI: " + (runtime.config().webEnabled() ? "enabled" : "disabled")
                    + " (not implemented yet)"));
            }, executor());
        }
    }

    private static final class ListCommand extends RuntimeAsyncCommand {
        private ListCommand(BlackboxRuntime runtime) {
            super("list", "List recent incidents", runtime);
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            return runAsync(context, () -> {
                List<IncidentFile> recent = listIncidents(runtime.incidentDir(), 10);
                if (recent.isEmpty()) {
                    context.sendMessage(Message.raw("No incidents found."));
                    return;
                }

                context.sendMessage(Message.raw("Recent incidents:"));
                for (IncidentFile file : recent) {
                    String headline = readHeadline(file.path()).orElse("<headline unavailable>");
                    context.sendMessage(Message.raw(file.id() + " - " + headline));
                    context.sendMessage(Message.raw("  " + file.path()));
                }
            }, executor());
        }
    }

    private static final class OpenCommand extends RuntimeAsyncCommand {
        private OpenCommand(BlackboxRuntime runtime) {
            super("open", "Show incident directory path", runtime);
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            return runAsync(context, () -> {
                context.sendMessage(Message.raw("Incidents dir: " + runtime.incidentDir()));
                context.sendMessage(Message.raw("Config: " + runtime.configPath()));
                if (runtime.config().webEnabled()) {
                    context.sendMessage(Message.raw("Web UI is enabled in config, but not implemented yet."));
                }
            }, executor());
        }
    }

    private static int countIncidents(Path incidentDir) {
        try {
            if (!Files.exists(incidentDir)) {
                return 0;
            }
            try (var stream = Files.list(incidentDir)) {
                return (int) stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .count();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static Optional<IncidentFile> findLatestIncident(Path incidentDir) {
        List<IncidentFile> listed = listIncidents(incidentDir, 1);
        if (listed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(listed.get(0));
    }

    private static List<IncidentFile> listIncidents(Path incidentDir, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<Path> zips;
        try {
            if (!Files.exists(incidentDir)) {
                return List.of();
            }
            try (var stream = Files.list(incidentDir)) {
                zips = stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .toList();
            }
        } catch (Exception e) {
            return List.of();
        }

        List<IncidentFile> incidents = new ArrayList<>(zips.size());
        for (Path zip : zips) {
            String id = parseIncidentId(zip.getFileName().toString());
            if (id == null) {
                continue;
            }
            incidents.add(new IncidentFile(id, zip, parseCreatedAtFromId(id)));
        }

        incidents.sort(Comparator
            .comparing((IncidentFile f) -> f.createdAt().orElse(Instant.EPOCH))
            .thenComparing(f -> f.path().getFileName().toString()));

        int from = Math.max(0, incidents.size() - limit);
        List<IncidentFile> slice = incidents.subList(from, incidents.size());
        slice = new ArrayList<>(slice);
        slice.sort(Comparator.comparing(IncidentFile::id).reversed());
        return slice;
    }

    private static String parseIncidentId(String fileName) {
        if (!fileName.endsWith(".zip")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - ".zip".length());
        if (base.startsWith("incident-")) {
            return base.substring("incident-".length());
        }
        return base;
    }

    private static Optional<Instant> parseCreatedAtFromId(String id) {
        int lastDash = id.lastIndexOf('-');
        if (lastDash <= 0) {
            return Optional.empty();
        }
        String timestamp = id.substring(0, lastDash);
        try {
            return Optional.of(OffsetDateTime.parse(timestamp, INCIDENT_TIMESTAMP).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> readHeadline(Path incidentZip) {
        try (ZipFile zip = new ZipFile(incidentZip.toFile())) {
            ZipEntry entry = zip.getEntry("incident.json");
            if (entry == null) {
                return Optional.empty();
            }
            byte[] bytes = zip.getInputStream(entry).readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8);
            return Optional.ofNullable(extractJsonString(json, "\"headline\":"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String extractJsonString(String json, String keyWithColon) throws IOException {
        int keyIndex = json.indexOf(keyWithColon);
        if (keyIndex < 0) {
            return null;
        }
        int i = keyIndex + keyWithColon.length();
        if (i >= json.length() || json.charAt(i) != '\"') {
            return null;
        }
        return readJsonStringLiteral(json, i);
    }

    private static String readJsonStringLiteral(String json, int openingQuoteIndex) throws IOException {
        if (openingQuoteIndex >= json.length() || json.charAt(openingQuoteIndex) != '\"') {
            return null;
        }
        StringBuilder out = new StringBuilder(64);
        for (int i = openingQuoteIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= json.length()) {
                return null;
            }
            char esc = json.charAt(++i);
            switch (esc) {
                case '\"' -> out.append('\"');
                case '\\' -> out.append('\\');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= json.length()) {
                        return null;
                    }
                    int codePoint = parseHex(json, i + 1, i + 5);
                    if (codePoint < 0) {
                        return null;
                    }
                    out.append((char) codePoint);
                    i += 4;
                }
                default -> out.append(esc);
            }
        }
        return null;
    }

    private static int parseHex(String value, int startInclusive, int endExclusive) {
        int codePoint = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            int digit = Character.digit(value.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            codePoint = (codePoint << 4) | digit;
        }
        return codePoint;
    }

    private record IncidentFile(String id, Path path, Optional<Instant> createdAt) {
    }
}
