package sh.harold.blackbox.core.trigger;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines trigger cooldowns and stall thresholds.
 */
public record TriggerPolicy(
    Duration cooldown,
    Duration debounce,
    long stallDegradedMs,
    long stallCriticalMs
) {
    public TriggerPolicy {
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(debounce, "debounce");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must be non-negative.");
        }
        if (debounce.isNegative()) {
            throw new IllegalArgumentException("debounce must be non-negative.");
        }
        if (stallDegradedMs <= 0 || stallCriticalMs <= 0) {
            throw new IllegalArgumentException("stall thresholds must be > 0.");
        }
        if (stallCriticalMs < stallDegradedMs) {
            throw new IllegalArgumentException("stallCriticalMs must be >= stallDegradedMs.");
        }
    }
}
