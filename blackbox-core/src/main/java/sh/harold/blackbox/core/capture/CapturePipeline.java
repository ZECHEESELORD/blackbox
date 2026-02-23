package sh.harold.blackbox.core.capture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import sh.harold.blackbox.core.bundle.BundleAttachment;
import sh.harold.blackbox.core.bundle.BundleBuilder;
import sh.harold.blackbox.core.env.TextRedactor;
import sh.harold.blackbox.core.incident.IncidentId;
import sh.harold.blackbox.core.incident.IncidentIds;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;
import sh.harold.blackbox.core.retention.RetentionManager;
import sh.harold.blackbox.core.trigger.TriggerDecision;
import sh.harold.blackbox.core.trigger.TriggerEngine;
import sh.harold.blackbox.core.trigger.TriggerEvent;
import sh.harold.blackbox.core.trigger.TriggerResult;

/**
 * Orchestrates trigger evaluation through capture and retention.
 */
public final class CapturePipeline {
    private final Clock clock;
    private final TriggerEngine triggerEngine;
    private final RecordingDumper dumper;
    private final BundleBuilder bundleBuilder;
    private final RetentionManager retentionManager;
    private final IncidentNotifier notifier;
    private final BundleExtrasProvider extrasProvider;
    private final Path incidentDir;
    private final Path tempDir;
    private final CapturePolicy policy;
    private final System.Logger logger;
    private final TextRedactor redactor;

    public CapturePipeline(
        Clock clock,
        TriggerEngine triggerEngine,
        RecordingDumper dumper,
        BundleBuilder bundleBuilder,
        RetentionManager retentionManager,
        IncidentNotifier notifier,
        Path incidentDir,
        Path tempDir,
        CapturePolicy policy,
        System.Logger logger
    ) {
        this(
            clock,
            triggerEngine,
            dumper,
            bundleBuilder,
            retentionManager,
            notifier,
            BundleExtrasProvider.none(),
            incidentDir,
            tempDir,
            policy,
            logger
        );
    }

    public CapturePipeline(
        Clock clock,
        TriggerEngine triggerEngine,
        RecordingDumper dumper,
        BundleBuilder bundleBuilder,
        RetentionManager retentionManager,
        IncidentNotifier notifier,
        BundleExtrasProvider extrasProvider,
        Path incidentDir,
        Path tempDir,
        CapturePolicy policy,
        System.Logger logger
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.triggerEngine = Objects.requireNonNull(triggerEngine, "triggerEngine");
        this.dumper = Objects.requireNonNull(dumper, "dumper");
        this.bundleBuilder = Objects.requireNonNull(bundleBuilder, "bundleBuilder");
        this.retentionManager = Objects.requireNonNull(retentionManager, "retentionManager");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.extrasProvider = Objects.requireNonNull(extrasProvider, "extrasProvider");
        this.incidentDir = Objects.requireNonNull(incidentDir, "incidentDir");
        this.tempDir = Objects.requireNonNull(tempDir, "tempDir");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.redactor = new TextRedactor(policy.redactPatterns());
    }

    public Optional<IncidentId> handle(TriggerEvent event) {
        if (!policy.enabled()) {
            return Optional.empty();
        }
        try {
            TriggerResult result = triggerEngine.evaluate(event);
            if (result.decision() != TriggerDecision.ACCEPT) {
                return Optional.empty();
            }

            IncidentId id = IncidentIds.next(clock);
            Instant createdAt = event.at();
            IncidentReport report = buildReport(id, createdAt, result, event);

            Files.createDirectories(tempDir);
            Files.createDirectories(incidentDir);

            Path tempRecording = tempDir.resolve(id.value() + ".jfr");
            Path dumpedRecording = dumper.dump(tempRecording);

            Path outputZip = incidentDir.resolve("incident-" + id.value() + ".zip");

            List<BundleAttachment> extras = List.of();
            try {
                extras = extrasProvider.extras(report, event);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Bundle extras provider failed.", e);
            }

            if (redactor.hasPatterns()) {
                extras = redactExtras(extras);
            }

            bundleBuilder.build(report, dumpedRecording, outputZip, extras);

            try {
                retentionManager.enforce(incidentDir, policy.retention());
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Retention enforcement failed.", e);
            }

            try {
                notifier.onIncident(report, outputZip);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Incident notification failed.", e);
            }

            try {
                Files.deleteIfExists(dumpedRecording);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Failed to clean up temp recording.", e);
            }

            return Optional.of(id);
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Capture pipeline failed.", e);
            return Optional.empty();
        }
    }

    private IncidentReport buildReport(
        IncidentId id,
        Instant createdAt,
        TriggerResult result,
        TriggerEvent event
    ) {
        IncidentMetadata meta = new IncidentMetadata(
            id,
            createdAt,
            result.severity(),
            event.kind().name(),
            event.scope(),
            result.headline()
        );

        String summaryLine = "Triggered by " + event.kind().name();
        IncidentSummary summary = new IncidentSummary(
            "Unknown",
            List.of(summaryLine),
            List.of("Review the incident report and recording.")
        );
        Map<String, String> context = event.attrs();
        if (redactor.hasPatterns()) {
            context = redactContext(context);
        }
        return new IncidentReport(meta, summary, context);
    }

    private static final java.util.Set<String> TEXT_EXTENSIONS = java.util.Set.of(
        ".txt", ".json", ".html", ".log", ".properties", ".xml", ".yaml", ".yml", ".cfg"
    );

    private List<BundleAttachment> redactExtras(List<BundleAttachment> extras) {
        List<BundleAttachment> result = new ArrayList<>(extras.size());
        for (BundleAttachment extra : extras) {
            if (isTextPath(extra.pathInZip())) {
                result.add(new BundleAttachment(extra.pathInZip(), redactor.redact(extra.data())));
            } else {
                result.add(extra);
            }
        }
        return result;
    }

    private Map<String, String> redactContext(Map<String, String> context) {
        Map<String, String> redacted = new LinkedHashMap<>(context.size());
        for (Map.Entry<String, String> entry : context.entrySet()) {
            redacted.put(entry.getKey(), redactor.redact(entry.getValue()));
        }
        return redacted;
    }

    private static boolean isTextPath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return TEXT_EXTENSIONS.contains(path.substring(dot).toLowerCase(java.util.Locale.ROOT));
    }
}
