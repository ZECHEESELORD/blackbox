package sh.harold.blackbox.core.incident;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates stable incident identifiers for the current JVM run.
 */
public final class IncidentIds {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSSZ").withLocale(Locale.ROOT);
    private static final AtomicLong COUNTER = new AtomicLong();

    private IncidentIds() {
    }

    public static IncidentId next(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), clock.getZone());
        String timestamp = FORMATTER.format(now);
        long counter = COUNTER.incrementAndGet();
        String suffix = Long.toUnsignedString(counter, 32);
        if (suffix.length() < 6) {
            suffix = "0".repeat(6 - suffix.length()) + suffix;
        }
        return new IncidentId(timestamp + "-" + suffix);
    }
}
