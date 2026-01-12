package sh.harold.blackbox.core.retention;

/**
 * Metrics from a retention enforcement run.
 */
public record RetentionStats(
    int scanned,
    int deleted,
    long bytesDeleted,
    int deleteFailures,
    long finalBytes,
    int finalCount
) {
}
