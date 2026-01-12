package sh.harold.blackbox.core.trigger;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import sh.harold.blackbox.core.incident.Severity;

/**
 * Applies cooldown and debounce policies to trigger events.
 */
public final class TriggerEngine {
    private final Clock clock;
    private final TriggerPolicy policy;
    private final Map<String, Instant> lastAcceptedByKey = new HashMap<>();
    private Instant lastAcceptedAt;

    public TriggerEngine(Clock clock, TriggerPolicy policy) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public TriggerResult evaluate(TriggerEvent event) {
        Objects.requireNonNull(event, "event");
        Instant now = event.at() == null ? clock.instant() : event.at();

        if (lastAcceptedAt != null) {
            Instant cooldownUntil = lastAcceptedAt.plus(policy.cooldown());
            if (now.isBefore(cooldownUntil)) {
                return new TriggerResult(TriggerDecision.COOLDOWN, Severity.INFO,
                    "Rejected: cooldown active");
            }
        }

        String key = event.kind() + "|" + event.scope();
        Instant lastForKey = lastAcceptedByKey.get(key);
        if (lastForKey != null) {
            Instant debounceUntil = lastForKey.plus(policy.debounce());
            if (now.isBefore(debounceUntil)) {
                return new TriggerResult(TriggerDecision.DEBOUNCE, Severity.INFO,
                    "Rejected: debounce active");
            }
        }

        TriggerResult accepted = decideAccepted(event);
        lastAcceptedAt = now;
        lastAcceptedByKey.put(key, now);
        return accepted;
    }

    private TriggerResult decideAccepted(TriggerEvent event) {
        if (event.kind() == TriggerKind.MANUAL) {
            String reason = event.attrs().get("reason");
            String headline = reason == null || reason.isBlank()
                ? "Manual capture"
                : "Manual capture: " + reason;
            return new TriggerResult(TriggerDecision.ACCEPT, Severity.INFO, headline);
        }
        if (event.kind() == TriggerKind.HEARTBEAT_STALL) {
            long stallMs = parseLong(event.attrs().get("stallMs"));
            Severity severity = Severity.INFO;
            if (stallMs >= policy.stallCriticalMs()) {
                severity = Severity.CRITICAL;
            } else if (stallMs >= policy.stallDegradedMs()) {
                severity = Severity.DEGRADED;
            }
            String headline = "Heartbeat stalled " + event.scope() + " (" + stallMs + "ms)";
            return new TriggerResult(TriggerDecision.ACCEPT, severity, headline);
        }
        return new TriggerResult(TriggerDecision.ACCEPT, Severity.INFO, "Capture triggered");
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
