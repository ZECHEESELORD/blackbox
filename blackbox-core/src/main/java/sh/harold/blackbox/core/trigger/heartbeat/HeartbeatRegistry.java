package sh.harold.blackbox.core.trigger.heartbeat;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last heartbeat timestamps per scope.
 */
public final class HeartbeatRegistry {
    private final Clock clock;
    private final Map<String, Instant> beats = new ConcurrentHashMap<>();

    public HeartbeatRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void beat(String scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope must be non-blank.");
        }
        beats.put(scope, clock.instant());
    }

    public Instant lastBeat(String scope) {
        return beats.get(scope);
    }

    public Set<String> scopes() {
        return Set.copyOf(beats.keySet());
    }
}
