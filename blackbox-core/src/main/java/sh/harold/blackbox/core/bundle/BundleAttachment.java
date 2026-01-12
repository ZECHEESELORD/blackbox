package sh.harold.blackbox.core.bundle;

import java.util.Objects;

/**
 * Binary attachment to include in the incident bundle zip.
 */
public record BundleAttachment(String pathInZip, byte[] data) {
    public BundleAttachment {
        Objects.requireNonNull(pathInZip, "pathInZip");
        Objects.requireNonNull(data, "data");
        if (pathInZip.isBlank()) {
            throw new IllegalArgumentException("pathInZip must be non-blank.");
        }
        if (pathInZip.startsWith("/")) {
            throw new IllegalArgumentException("pathInZip must be relative.");
        }
        if (pathInZip.contains("\\")) {
            throw new IllegalArgumentException("pathInZip must use forward slashes.");
        }
        String[] segments = pathInZip.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("pathInZip must be normalized.");
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("pathInZip must not contain dot segments.");
            }
        }
    }
}
