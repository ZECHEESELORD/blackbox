package sh.harold.blackbox.core.trigger;

import java.util.Objects;
import sh.harold.blackbox.core.incident.Severity;

/**
 * Outcome of trigger evaluation.
 */
public record TriggerResult(
    TriggerDecision decision,
    Severity severity,
    String headline
) {
    public TriggerResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(headline, "headline");
    }
}
