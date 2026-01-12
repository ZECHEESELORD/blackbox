package sh.harold.blackbox.core.jfr;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrControllerTest {

    @Test
    void dumpsRecordingWithMarkerEvent(@TempDir Path tempDir) throws Exception {
        assertTrue(FlightRecorder.isAvailable(), "JFR is not available in this runtime.");
        Path dumpPath = tempDir.resolve("recording.jfr");

        try (JfrController controller = new JfrController(Duration.ofSeconds(60), 16L * 1024L * 1024L,
            "blackbox-test")) {
            controller.start();
            controller.enableEvent("sh.harold.blackbox.marker").withThreshold(Duration.ZERO);

            for (int i = 0; i < 3; i++) {
                BlackboxMarkerEvent event = new BlackboxMarkerEvent();
                event.message = "marker-" + i;
                event.commit();
            }

            controller.dump(dumpPath);
        }

        assertTrue(Files.exists(dumpPath), "Expected JFR dump to exist.");
        assertTrue(Files.size(dumpPath) > 0, "Expected JFR dump to be non-empty.");

        boolean foundMarker = false;
        try (RecordingFile recordingFile = new RecordingFile(dumpPath)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if ("sh.harold.blackbox.marker".equals(event.getEventType().getName())) {
                    foundMarker = true;
                    break;
                }
            }
        }

        assertTrue(foundMarker, "Expected at least one marker event in the recording.");
    }
}
