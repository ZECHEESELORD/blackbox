package sh.harold.blackbox.core.jfr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.text.ParseException;
import jdk.jfr.Configuration;
import jdk.jfr.EventSettings;
import jdk.jfr.Recording;

/**
 * Controls a single rolling JFR recording and exposes a minimal API for dumping it.
 */
public final class JfrController implements AutoCloseable {
    private static final String DEFAULT_CONFIGURATION = "default";
    private static final String DUMP_MARKER_PREFIX = "blackbox dump:";

    private final Duration maxAge;
    private final long maxSizeBytes;
    private final String recordingName;
    private Recording recording;
    private final System.Logger logger = System.getLogger(JfrController.class.getName());

    public JfrController(Duration maxAge, long maxSizeBytes, String recordingName) {
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.maxSizeBytes = maxSizeBytes;
        this.recordingName = Objects.requireNonNull(recordingName, "recordingName");
    }

    public void start() {
        if (recording != null) {
            return;
        }
        Recording created = createConfiguredRecording(DEFAULT_CONFIGURATION);
        created.setName(recordingName);
        created.setToDisk(true);
        created.setMaxAge(maxAge);
        created.setMaxSize(maxSizeBytes);
        enableMarkerEvent(created);
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
        BlackboxMarkerEvent marker = new BlackboxMarkerEvent();
        marker.message = DUMP_MARKER_PREFIX + " " + target.getFileName();
        marker.commit();
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

    private Recording createConfiguredRecording(String configurationName) {
        Recording configured = tryLoadConfiguration(configurationName);
        if (configured != null) {
            return configured;
        }

        if (!"profile".equals(configurationName)) {
            Recording profile = tryLoadConfiguration("profile");
            if (profile != null) {
                return profile;
            }
        }

        logger.log(System.Logger.Level.WARNING, "Failed to load JFR configurations. Falling back to an unconfigured recording.");
        return new Recording();
    }

    private void enableMarkerEvent(Recording recording) {
        try {
            recording.enable(BlackboxMarkerEvent.class)
                .withoutStackTrace()
                .withThreshold(Duration.ZERO);
        } catch (IllegalArgumentException e) {
            logger.log(System.Logger.Level.WARNING, "Failed to enable marker event.", e);
        }
    }

    private Recording tryLoadConfiguration(String configurationName) {
        try {
            return new Recording(Configuration.getConfiguration(configurationName));
        } catch (IOException | ParseException e) {
            logger.log(System.Logger.Level.DEBUG, "Failed to load JFR configuration '" + configurationName + "'.", e);
            return null;
        }
    }
}
