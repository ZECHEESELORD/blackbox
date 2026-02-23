package sh.harold.blackbox.hytale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

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
