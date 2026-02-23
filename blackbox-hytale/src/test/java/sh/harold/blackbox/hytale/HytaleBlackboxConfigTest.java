package sh.harold.blackbox.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.blackbox.core.config.BlackboxConfig;

class HytaleBlackboxConfigTest {

    @Test
    void parsesCaptureAndDisabledEventsFields(@TempDir Path dataDir) throws Exception {
        Path configPath = HytaleBlackboxConfig.path(dataDir);
        Files.writeString(configPath, """
            {
              "Version": 1,
              "Jfr": {
                "DisabledEvents": ["sh.harold.blackbox.marker", "jdk.CPULoad"]
              },
              "Capture": {
                "Enabled": false,
                "AllowPluginExtras": false,
                "LogTailLines": 12,
                "RedactPatterns": ["CUSTOM_SECRET_[A-Z0-9]+"]
              }
            }
            """, StandardCharsets.UTF_8);

        BlackboxConfig config = HytaleBlackboxConfig.loadOrCreate(dataDir, System.getLogger("hytale-config-test"));

        assertEquals(List.of("sh.harold.blackbox.marker", "jdk.CPULoad"), config.jfrDisabledEvents());
        assertFalse(config.capturePolicy().enabled());
        assertFalse(config.capturePolicy().allowPluginExtras());
        assertEquals(12, config.capturePolicy().logTailLines());
        assertEquals(List.of("CUSTOM_SECRET_[A-Z0-9]+"), config.capturePolicy().redactPatterns());
    }

    @Test
    void defaultsNewFieldsWhenOmitted(@TempDir Path dataDir) throws Exception {
        Path configPath = HytaleBlackboxConfig.path(dataDir);
        Files.writeString(configPath, """
            {
              "Version": 1
            }
            """, StandardCharsets.UTF_8);

        BlackboxConfig config = HytaleBlackboxConfig.loadOrCreate(dataDir, System.getLogger("hytale-config-test"));

        assertTrue(config.jfrDisabledEvents().isEmpty());
        assertTrue(config.capturePolicy().enabled());
        assertTrue(config.capturePolicy().allowPluginExtras());
        assertEquals(500, config.capturePolicy().logTailLines());
        assertFalse(config.capturePolicy().redactPatterns().isEmpty());
    }
}
