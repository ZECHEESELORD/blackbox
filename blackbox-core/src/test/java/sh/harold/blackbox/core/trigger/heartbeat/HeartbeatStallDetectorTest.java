package sh.harold.blackbox.core.trigger.heartbeat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import sh.harold.blackbox.core.testutil.MutableClock;

class HeartbeatStallDetectorTest {

    @Test
    void emitsOncePerStallTransition() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);
        HeartbeatRegistry registry = new HeartbeatRegistry(clock);
        HeartbeatStallDetector detector = new HeartbeatStallDetector(clock, registry, 1000);

        registry.beat("world");

        assertEquals(0, detector.check().size());

        clock.advance(Duration.ofMillis(1100));
        assertEquals(1, detector.check().size());

        clock.advance(Duration.ofMillis(200));
        assertEquals(0, detector.check().size());

        registry.beat("world");
        clock.advance(Duration.ofMillis(1200));
        assertEquals(1, detector.check().size());
    }
}
