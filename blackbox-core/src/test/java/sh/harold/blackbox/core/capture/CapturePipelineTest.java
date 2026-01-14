package sh.harold.blackbox.core.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.blackbox.core.bundle.BundleBuilder;
import sh.harold.blackbox.core.incident.IncidentId;
import sh.harold.blackbox.core.retention.FileDeleter;
import sh.harold.blackbox.core.retention.RetentionManager;
import sh.harold.blackbox.core.retention.RetentionPolicy;
import sh.harold.blackbox.core.testutil.MutableClock;
import sh.harold.blackbox.core.trigger.TriggerEngine;
import sh.harold.blackbox.core.trigger.TriggerEvent;
import sh.harold.blackbox.core.trigger.TriggerKind;
import sh.harold.blackbox.core.trigger.TriggerPolicy;

class CapturePipelineTest {

    @Test
    void burstCooldownCreatesSingleZipThenAnotherAfterCooldown(@TempDir Path tempDir) throws Exception {
        Path incidentDir = tempDir.resolve("incidents");
        Path tempRecordings = tempDir.resolve("temp");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);

        TriggerPolicy policy = new TriggerPolicy(Duration.ofSeconds(30), Duration.ZERO, 1000, 5000);
        TriggerEngine engine = new TriggerEngine(clock, policy);
        CapturePipeline pipeline = new CapturePipeline(
            clock,
            engine,
            new FakeRecordingDumper(new byte[] {1, 2, 3}),
            new BundleBuilder(clock),
            new RetentionManager(clock, System.getLogger("retention-test"), FileDeleter.defaultDeleter()),
            IncidentNotifier.noop(),
            incidentDir,
            tempRecordings,
            new CapturePolicy(new RetentionPolicy(0, 0L, null)),
            System.getLogger("capture-test")
        );

        TriggerEvent event = new TriggerEvent(TriggerKind.MANUAL, "world", clock.instant(), Map.of());
        for (int i = 0; i < 100; i++) {
            pipeline.handle(event);
        }

        assertEquals(1, countZips(incidentDir));

        clock.advance(Duration.ofSeconds(30));
        TriggerEvent laterEvent = new TriggerEvent(TriggerKind.MANUAL, "world", clock.instant(), Map.of());
        pipeline.handle(laterEvent);

        assertEquals(2, countZips(incidentDir));
    }

    @Test
    void stallSeverityIsReflectedInIncidentJson(@TempDir Path tempDir) throws Exception {
        Path incidentDir = tempDir.resolve("incidents");
        Path tempRecordings = tempDir.resolve("temp");
        Clock clock = Clock.fixed(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);

        TriggerPolicy policy = new TriggerPolicy(Duration.ZERO, Duration.ZERO, 2000, 6000);
        TriggerEngine engine = new TriggerEngine(clock, policy);
        CapturePipeline pipeline = new CapturePipeline(
            clock,
            engine,
            new FakeRecordingDumper(new byte[] {9, 9, 9}),
            new BundleBuilder(clock),
            new RetentionManager(clock, System.getLogger("retention-test"), FileDeleter.defaultDeleter()),
            IncidentNotifier.noop(),
            incidentDir,
            tempRecordings,
            new CapturePolicy(new RetentionPolicy(0, 0L, null)),
            System.getLogger("capture-test")
        );

        Optional<IncidentId> degradedId = pipeline.handle(new TriggerEvent(
            TriggerKind.HEARTBEAT_STALL,
            "world",
            clock.instant(),
            Map.of("stallMs", "2000")
        ));

        Optional<IncidentId> criticalId = pipeline.handle(new TriggerEvent(
            TriggerKind.HEARTBEAT_STALL,
            "world",
            clock.instant(),
            Map.of("stallMs", "6000")
        ));

        assertTrue(degradedId.isPresent());
        assertTrue(criticalId.isPresent());

        assertTrue(readSeverity(incidentDir, degradedId.get()).contains("\"severity\":\"DEGRADED\""));
        assertTrue(readSeverity(incidentDir, criticalId.get()).contains("\"severity\":\"CRITICAL\""));
    }

    private static int countZips(Path incidentDir) throws Exception {
        if (!Files.exists(incidentDir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(incidentDir)) {
            return (int) stream.filter(path -> path.getFileName().toString().endsWith(".zip")).count();
        }
    }

    private static String readSeverity(Path incidentDir, IncidentId id) throws Exception {
        Path zipPath = incidentDir.resolve("incident-" + id.value() + ".zip");
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry("incident.json");
            byte[] bytes = zip.getInputStream(entry).readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private record FakeRecordingDumper(byte[] bytes) implements RecordingDumper {

        @Override
            public Path dump(Path target) throws Exception {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                return target;
            }
        }
}
