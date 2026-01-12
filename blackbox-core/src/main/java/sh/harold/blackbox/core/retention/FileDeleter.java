package sh.harold.blackbox.core.retention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstracts deletion to allow deterministic testing.
 */
@FunctionalInterface
public interface FileDeleter {
    void delete(Path path) throws IOException;

    static FileDeleter defaultDeleter() {
        return Files::deleteIfExists;
    }
}
