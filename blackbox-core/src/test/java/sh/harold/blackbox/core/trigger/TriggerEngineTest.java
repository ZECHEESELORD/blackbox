package sh.harold.blackbox.core.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sh.harold.blackbox.core.testutil.MutableClock;

class TriggerEngineTest {

    @Test
    void cooldownAndDebounceApplyInOrder() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);
        TriggerPolicy policy = new TriggerPolicy(Duration.ofSeconds(30), Duration.ofSeconds(60), 1000, 5000);
        TriggerEngine engine = new TriggerEngine(clock, policy);

        TriggerEvent event = new TriggerEvent(TriggerKind.MANUAL, "world", clock.instant(), Map.of());
        TriggerResult first = engine.evaluate(event);
        assertEquals(TriggerDecision.ACCEPT, first.decision());

        TriggerResult second = engine.evaluate(event);
        assertEquals(TriggerDecision.COOLDOWN, second.decision());

        clock.advance(Duration.ofSeconds(31));
        TriggerEvent laterEvent = new TriggerEvent(TriggerKind.MANUAL, "world", clock.instant(), Map.of());
        TriggerResult third = engine.evaluate(laterEvent);
        assertEquals(TriggerDecision.DEBOUNCE, third.decision());

        clock.advance(Duration.ofSeconds(30));
        TriggerEvent finalEvent = new TriggerEvent(TriggerKind.MANUAL, "world", clock.instant(), Map.of());
        TriggerResult fourth = engine.evaluate(finalEvent);
        assertEquals(TriggerDecision.ACCEPT, fourth.decision());
    }
}
