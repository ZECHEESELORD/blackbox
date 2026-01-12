package sh.harold.blackbox.core.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetentionManagerTest {

    @Test
    void enforce_maxCount_deletesOldest(@TempDir Path tempDir) {
        createIncidentZip(tempDir, "incident-20260111-010000.000Z-000001.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-010001.000Z-000002.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-010002.000Z-000003.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-010003.000Z-000004.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-010004.000Z-000005.zip", 10);

        RetentionManager manager = new RetentionManager(
            Clock.fixed(Instant.parse("2026-01-11T02:00:00Z"), ZoneOffset.UTC),
            System.getLogger("retention-test"),
            FileDeleter.defaultDeleter()
        );
        RetentionPolicy policy = new RetentionPolicy(2, 0L, null);

        RetentionStats stats = manager.enforce(tempDir, policy);

        assertTrue(Files.exists(tempDir.resolve("incident-20260111-010003.000Z-000004.zip")));
        assertTrue(Files.exists(tempDir.resolve("incident-20260111-010004.000Z-000005.zip")));
        assertTrue(!Files.exists(tempDir.resolve("incident-20260111-010000.000Z-000001.zip")));
        assertTrue(!Files.exists(tempDir.resolve("incident-20260111-010001.000Z-000002.zip")));
        assertTrue(!Files.exists(tempDir.resolve("incident-20260111-010002.000Z-000003.zip")));

        assertEquals(3, stats.deleted());
        assertEquals(0, stats.deleteFailures());
    }

    @Test
    void enforce_maxAge_deletesOldestBeyondCutoff(@TempDir Path tempDir) {
        createIncidentZip(tempDir, "incident-20260111-003000.000Z-000001.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-005900.000Z-000002.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-013000.000Z-000003.zip", 10);
        createIncidentZip(tempDir, "incident-20260111-015900.000Z-000004.zip", 10);

        Clock clock = Clock.fixed(Instant.parse("2026-01-11T02:00:00Z"), ZoneOffset.UTC);
        RetentionManager manager = new RetentionManager(
            clock,
            System.getLogger("retention-test"),
            FileDeleter.defaultDeleter()
        );
        RetentionPolicy policy = new RetentionPolicy(0, 0L, Duration.ofHours(1));

        RetentionStats stats = manager.enforce(tempDir, policy);

        assertTrue(!Files.exists(tempDir.resolve("incident-20260111-003000.000Z-000001.zip")));
        assertTrue(!Files.exists(tempDir.resolve("incident-20260111-005900.000Z-000002.zip")));
        assertTrue(Files.exists(tempDir.resolve("incident-20260111-013000.000Z-000003.zip")));
        assertTrue(Files.exists(tempDir.resolve("incident-20260111-015900.000Z-000004.zip")));

        assertEquals(2, stats.deleted());
        assertEquals(0, stats.deleteFailures());
    }

    @Test
    void enforce_maxTotalBytes_deletesUntilUnderLimit(@TempDir Path tempDir) {
        createIncidentZip(tempDir, "incident-20260111-010000.000Z-000001.zip", 100);
        createIncidentZip(tempDir, "incident-20260111-010001.000Z-000002.zip", 100);
        createIncidentZip(tempDir, "incident-20260111-010002.000Z-000003.zip", 100);
        createIncidentZip(tempDir, "incident-20260111-010003.000Z-000004.zip", 100);
        createIncidentZip(tempDir, "incident-20260111-010004.000Z-000005.zip", 100);

        RetentionManager manager = new RetentionManager(
            Clock.fixed(Instant.parse("2026-01-11T02:00:00Z"), ZoneOffset.UTC),
            System.getLogger("retention-test"),
            FileDeleter.defaultDeleter()
        );
        RetentionPolicy policy = new RetentionPolicy(0, 250L, null);

        RetentionStats stats = manager.enforce(tempDir, policy);

        assertEquals(2, stats.finalCount());
        assertTrue(stats.finalBytes() <= 250L);
    }

    @Test
    void partialFailure_doesNotDeleteNewest(@TempDir Path tempDir) {
        Path oldest = createIncidentZip(tempDir, "incident-20260111-010000.000Z-000001.zip", 10);
        Path middle = createIncidentZip(tempDir, "incident-20260111-010001.000Z-000002.zip", 10);
        Path newest = createIncidentZip(tempDir, "incident-20260111-010002.000Z-000003.zip", 10);

        FileDeleter deleter = path -> {
            if (path.equals(newest)) {
                throw new AssertionError("Newest incident should never be deleted.");
            }
            if (path.equals(oldest)) {
                throw new IOException("Simulated delete failure.");
            }
            Files.deleteIfExists(path);
        };

        RetentionManager manager = new RetentionManager(
            Clock.fixed(Instant.parse("2026-01-11T02:00:00Z"), ZoneOffset.UTC),
            System.getLogger("retention-test"),
            deleter
        );
        RetentionPolicy policy = new RetentionPolicy(1, 0L, null);

        RetentionStats stats = manager.enforce(tempDir, policy);

        assertTrue(Files.exists(newest));
        assertTrue(stats.deleteFailures() > 0);
    }

    private static Path createIncidentZip(Path dir, String fileName, int bytes) {
        try {
            Path path = dir.resolve(fileName);
            byte[] content = new byte[bytes];
            for (int i = 0; i < bytes; i++) {
                content[i] = (byte) (i & 0xFF);
            }
            Files.write(path, content);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test incident file.", e);
        }
    }
}
