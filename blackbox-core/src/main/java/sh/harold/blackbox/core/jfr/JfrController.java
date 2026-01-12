package sh.harold.blackbox.core.jfr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import jdk.jfr.EventSettings;
import jdk.jfr.Recording;

/**
 * Controls a single rolling JFR recording and exposes a minimal API for dumping it.
 */
public final class JfrController implements AutoCloseable {
    private final Duration maxAge;
    private final long maxSizeBytes;
    private final String recordingName;
    private Recording recording;

    public JfrController(Duration maxAge, long maxSizeBytes, String recordingName) {
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.maxSizeBytes = maxSizeBytes;
        this.recordingName = Objects.requireNonNull(recordingName, "recordingName");
    }

    public void start() {
        if (recording != null) {
            return;
        }
        Recording created = new Recording();
        created.setName(recordingName);
        created.setToDisk(true);
        created.setMaxAge(maxAge);
        created.setMaxSize(maxSizeBytes);
        created.start();
        this.recording = created;
    }

    public EventSettings enableEvent(String eventName) {
        return requireRecording().enable(eventName);
    }

    public void dump(Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        requireRecording().dump(target);
    }

    @Override
    public void close() {
        if (recording == null) {
            return;
        }
        recording.stop();
        recording.close();
        recording = null;
    }

    private Recording requireRecording() {
        if (recording == null) {
            throw new IllegalStateException("Recording has not been started.");
        }
        return recording;
    }
}
