package sh.harold.blackbox.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleBundleExtrasProviderTest {

    @Test
    void classPresenceCheckDoesNotInitializeClass() {
        InitMarker.INITIALIZED.set(false);
        String className = InitProbe.class.getName();

        boolean present = HytaleBundleExtrasProvider.isClassPresent(
            className,
            HytaleBundleExtrasProviderTest.class.getClassLoader()
        );

        assertTrue(present);
        assertFalse(InitMarker.INITIALIZED.get(), "Class initialization should not run.");
    }

    @Test
    void classPresenceCheckReturnsFalseForMissingClass() {
        boolean present = HytaleBundleExtrasProvider.isClassPresent(
            "not.present.ClassName",
            HytaleBundleExtrasProviderTest.class.getClassLoader()
        );
        assertFalse(present);
    }

    @Test
    void classPresenceCheckGuardsThrowableFromLoader() {
        ClassLoader throwingLoader = new ThrowingLoader(HytaleBundleExtrasProviderTest.class.getClassLoader());
        boolean present = HytaleBundleExtrasProvider.isClassPresent("boom.Broken", throwingLoader);
        assertFalse(present);
    }

    @Test
    void readTailRespectsByteBoundForLargeSingleLine(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("latest.log");
        String prefix = "x".repeat(20_000);
        String suffix = "::THE_END::";
        Files.writeString(log, prefix + suffix, StandardCharsets.UTF_8);

        String tail = HytaleBundleExtrasProvider.readTail(log, 1, 64);

        assertTrue(tail.endsWith(suffix));
        assertTrue(tail.getBytes(StandardCharsets.UTF_8).length <= 64);
    }

    @Test
    void readTailKeepsLineBehaviorWhenWithinByteBound(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("lines.log");
        Files.writeString(log, "a\nb\nc\n", StandardCharsets.UTF_8);

        String tail = HytaleBundleExtrasProvider.readTail(log, 2, 1024);

        assertEquals("b\nc\n", tail);
    }

    private static final class InitMarker {
        private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    }

    private static final class InitProbe {
        static {
            InitMarker.INITIALIZED.set(true);
        }
    }

    private static final class ThrowingLoader extends ClassLoader {
        private ThrowingLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if ("boom.Broken".equals(name)) {
                throw new LinkageError("simulated linkage failure");
            }
            return super.loadClass(name, resolve);
        }
    }
}
