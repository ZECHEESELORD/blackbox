package sh.harold.blackbox.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import sh.harold.blackbox.core.config.BlackboxConfig;

class HytaleBlackboxConfigTest {

    @Test
    void parsesCaptureAndDisabledEventsFields() throws Exception {
        Object fileConfig = newFileConfig();
        Object jfr = getField(fileConfig, "jfr");
        Object capture = getField(fileConfig, "capture");

        setField(jfr, "disabledEvents", List.of("sh.harold.blackbox.marker", "jdk.CPULoad"));
        setField(capture, "enabled", false);
        setField(capture, "allowPluginExtras", false);
        setField(capture, "logTailLines", 12);
        setField(capture, "redactPatterns", new ArrayList<>(List.of("CUSTOM_SECRET_[A-Z0-9]+")));

        BlackboxConfig config = toCoreConfig(fileConfig);

        assertEquals(List.of("sh.harold.blackbox.marker", "jdk.CPULoad"), config.jfrDisabledEvents());
        assertFalse(config.capturePolicy().enabled());
        assertFalse(config.capturePolicy().allowPluginExtras());
        assertEquals(12, config.capturePolicy().logTailLines());
        assertEquals(List.of("CUSTOM_SECRET_[A-Z0-9]+"), config.capturePolicy().redactPatterns());
    }

    @Test
    void defaultsNewFieldsWhenOmitted() throws Exception {
        Object fileConfig = newFileConfig();
        BlackboxConfig config = toCoreConfig(fileConfig);

        assertTrue(config.jfrDisabledEvents().isEmpty());
        assertTrue(config.capturePolicy().enabled());
        assertTrue(config.capturePolicy().allowPluginExtras());
        assertEquals(500, config.capturePolicy().logTailLines());
        assertFalse(config.capturePolicy().redactPatterns().isEmpty());
    }

    private static Object newFileConfig() throws Exception {
        Class<?> fileConfigClass = fileConfigClass();
        var ctor = fileConfigClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static BlackboxConfig toCoreConfig(Object fileConfig) throws Exception {
        Method method = fileConfigClass().getDeclaredMethod("toCoreConfig", System.Logger.class);
        method.setAccessible(true);
        return (BlackboxConfig) method.invoke(fileConfig, System.getLogger("hytale-config-test"));
    }

    private static Class<?> fileConfigClass() {
        for (Class<?> nested : HytaleBlackboxConfig.class.getDeclaredClasses()) {
            if ("FileConfig".equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new IllegalStateException("FileConfig class not found.");
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
