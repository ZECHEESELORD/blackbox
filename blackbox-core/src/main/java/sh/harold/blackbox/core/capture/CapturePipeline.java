package sh.harold.blackbox.core.capture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import sh.harold.blackbox.core.bundle.BundleBuilder;
import sh.harold.blackbox.core.incident.IncidentId;
import sh.harold.blackbox.core.incident.IncidentIds;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;
import sh.harold.blackbox.core.incident.Severity;
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
    private final Path incidentDir;
    private final Path tempDir;
    private final CapturePolicy policy;
    private final System.Logger logger;

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
        this.clock = Objects.requireNonNull(clock, "clock");
        this.triggerEngine = Objects.requireNonNull(triggerEngine, "triggerEngine");
        this.dumper = Objects.requireNonNull(dumper, "dumper");
        this.bundleBuilder = Objects.requireNonNull(bundleBuilder, "bundleBuilder");
        this.retentionManager = Objects.requireNonNull(retentionManager, "retentionManager");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.incidentDir = Objects.requireNonNull(incidentDir, "incidentDir");
        this.tempDir = Objects.requireNonNull(tempDir, "tempDir");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Optional<IncidentId> handle(TriggerEvent event) {
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
            bundleBuilder.build(report, dumpedRecording, outputZip, List.of());

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

    private static IncidentReport buildReport(
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
        return new IncidentReport(meta, summary);
    }
}
