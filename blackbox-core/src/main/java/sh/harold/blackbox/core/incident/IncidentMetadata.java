package sh.harold.blackbox.core.incident;

import java.time.Instant;
import java.util.Objects;

/**
 * Identifies what happened and when.
 */
public record IncidentMetadata(
    IncidentId id,
    Instant createdAt,
    Severity severity,
    String trigger,
    String world,
    String headline
) {
    public IncidentMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(headline, "headline");
    }
}
