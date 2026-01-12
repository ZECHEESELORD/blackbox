package sh.harold.blackbox.core.capture;

import java.nio.file.Path;

/**
 * Dumps a recording to the provided target.
 */
@FunctionalInterface
public interface RecordingDumper {
    Path dump(Path target) throws Exception;
}
