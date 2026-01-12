package sh.harold.blackbox.core.env;

/**
 * Collects minimal JVM and OS metadata for incident bundles.
 */
public final class EnvCollector {
    private EnvCollector() {
    }

    public static String jvmInfo() {
        return new StringBuilder()
            .append("java.runtime.version=").append(System.getProperty("java.runtime.version")).append('\n')
            .append("java.vm.name=").append(System.getProperty("java.vm.name")).append('\n')
            .append("java.vm.vendor=").append(System.getProperty("java.vm.vendor")).append('\n')
            .append("java.home=").append(System.getProperty("java.home")).append('\n')
            .toString();
    }

    public static String osInfo() {
        return new StringBuilder()
            .append("os.name=").append(System.getProperty("os.name")).append('\n')
            .append("os.version=").append(System.getProperty("os.version")).append('\n')
            .append("os.arch=").append(System.getProperty("os.arch")).append('\n')
            .toString();
    }
}
