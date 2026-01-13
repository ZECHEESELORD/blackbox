package sh.harold.blackbox.core.notify.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import sh.harold.blackbox.core.incident.IncidentId;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;
import sh.harold.blackbox.core.incident.Severity;
import sh.harold.blackbox.core.testutil.MutableClock;

class DiscordWebhookNotifierTest {

    @Test
    void payload_format_test() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);
        FakeWebhookTransport transport = new FakeWebhookTransport();
        DiscordWebhookNotifier notifier = new DiscordWebhookNotifier(
            clock,
            System.getLogger("notify-test"),
            new DiscordWebhookConfig("https://example.test/webhook", Duration.ZERO, Duration.ofSeconds(2), "Blackbox"),
            transport,
            Runnable::run
        );

        IncidentReport report = reportWithEscapes(clock);
        notifier.onIncident(report, Path.of("bundle.zip"));

        assertEquals(1, transport.callCount);
        assertNotNull(transport.lastUri);
        assertNotNull(transport.lastPayload);
        assertTrue(transport.lastPayload.contains("\"content\""));
        assertTrue(transport.lastPayload.contains("\"username\""));
        assertTrue(transport.lastPayload.contains("\\\""));
        assertTrue(transport.lastPayload.contains("\\\\"));
        assertTrue(transport.lastPayload.contains("\\n"));
    }

    @Test
    void rate_limiting_test() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-11T00:00:00Z"), ZoneOffset.UTC);
        FakeWebhookTransport transport = new FakeWebhookTransport();
        DiscordWebhookNotifier notifier = new DiscordWebhookNotifier(
            clock,
            System.getLogger("notify-test"),
            new DiscordWebhookConfig("https://example.test/webhook", Duration.ofSeconds(10), Duration.ofSeconds(2), ""),
            transport,
            Runnable::run
        );

        IncidentReport report = reportWithEscapes(clock);
        notifier.onIncident(report, Path.of("bundle.zip"));
        notifier.onIncident(report, Path.of("bundle.zip"));
        notifier.onIncident(report, Path.of("bundle.zip"));
        assertEquals(1, transport.callCount);

        clock.advance(Duration.ofSeconds(9));
        notifier.onIncident(report, Path.of("bundle.zip"));
        assertEquals(1, transport.callCount);

        clock.advance(Duration.ofSeconds(1));
        notifier.onIncident(report, Path.of("bundle.zip"));
        assertEquals(2, transport.callCount);

        transport.throwOnPost = true;
        assertDoesNotThrow(() -> notifier.onIncident(report, Path.of("bundle.zip")));
    }

    private static IncidentReport reportWithEscapes(MutableClock clock) {
        IncidentMetadata meta = new IncidentMetadata(
            new IncidentId("20260111-000000.000Z-abcdef"),
            clock.instant(),
            Severity.CRITICAL,
            "manual",
            "world-one",
            "Headline \"quoted\" and path C:\\temp\\file\nLine two"
        );
        IncidentSummary summary = new IncidentSummary(
            "Unknown",
            List.of("One", "Two"),
            List.of("Next")
        );
        return new IncidentReport(meta, summary);
    }

    private static final class FakeWebhookTransport implements WebhookTransport {
        private URI lastUri;
        private String lastPayload;
        private int callCount;
        private boolean throwOnPost;

        @Override
        public void post(URI uri, String jsonPayload) throws Exception {
            callCount++;
            lastUri = uri;
            lastPayload = jsonPayload;
            if (throwOnPost) {
                throw new Exception("Simulated transport failure.");
            }
        }
    }
}
