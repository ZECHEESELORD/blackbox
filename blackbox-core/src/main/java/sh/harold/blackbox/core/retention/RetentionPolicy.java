package sh.harold.blackbox.core.retention;

import java.time.Duration;

/**
 * Defines retention limits for stored incident bundles.
 */
public record RetentionPolicy(
    int maxCount,
    long maxTotalBytes,
    Duration maxAge
) {
    public RetentionPolicy {
        if (maxCount < 0) {
            throw new IllegalArgumentException("maxCount must be >= 0.");
        }
        if (maxTotalBytes < 0) {
            throw new IllegalArgumentException("maxTotalBytes must be >= 0.");
        }
        if (maxAge != null && maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must be non-negative.");
        }
    }
}
