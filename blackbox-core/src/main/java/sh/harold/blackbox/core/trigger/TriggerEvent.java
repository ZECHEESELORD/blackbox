package sh.harold.blackbox.core.trigger;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Trigger signal emitted by detectors or manual sources.
 */
public record TriggerEvent(
    TriggerKind kind,
    String scope,
    Instant at,
    Map<String, String> attrs
) {
    public TriggerEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(attrs, "attrs");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope must be non-blank.");
        }
        attrs = Map.copyOf(attrs);
    }
}
