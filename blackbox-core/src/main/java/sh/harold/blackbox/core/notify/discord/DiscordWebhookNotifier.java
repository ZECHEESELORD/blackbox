package sh.harold.blackbox.core.notify.discord;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import sh.harold.blackbox.core.capture.IncidentNotifier;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.json.JsonWriter;

/**
 * Sends incident notifications to a Discord webhook.
 */
public final class DiscordWebhookNotifier implements IncidentNotifier {
    private final Clock clock;
    private final System.Logger logger;
    private final DiscordWebhookConfig config;
    private final WebhookTransport transport;
    private final Executor executor;
    private final Object lock = new Object();
    private Instant lastSentAt;

    public DiscordWebhookNotifier(
        Clock clock,
        System.Logger logger,
        DiscordWebhookConfig config,
        WebhookTransport transport,
        Executor executor
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void onIncident(IncidentReport report, Path bundleZip) {
        if (config.webhookUrl().isBlank()) {
            return;
        }
        Instant now = clock.instant();
        if (!shouldSend(now)) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(config.webhookUrl());
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Invalid Discord webhook URL.", e);
            return;
        }

        String payload;
        try {
            payload = buildPayload(report);
        } catch (IOException e) {
            logger.log(System.Logger.Level.WARNING, "Failed to build Discord webhook payload.", e);
            return;
        }

        executor.execute(() -> {
            try {
                transport.post(uri, payload);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Discord webhook delivery failed.", e);
            }
        });
    }

    private boolean shouldSend(Instant now) {
        synchronized (lock) {
            if (lastSentAt == null) {
                lastSentAt = now;
                return true;
            }
            Instant nextAllowed = lastSentAt.plus(config.cooldown());
            if (now.isBefore(nextAllowed)) {
                return false;
            }
            lastSentAt = now;
            return true;
        }
    }

    private String buildPayload(IncidentReport report) throws IOException {
        IncidentMetadata meta = report.meta();
        String scope = meta.world() == null ? "unknown" : meta.world();
        String content = "[" + meta.severity().name() + "] " + meta.headline() + " (scope: " + scope + ")";

        StringWriter writer = new StringWriter();
        JsonWriter json = new JsonWriter(writer);
        json.beginObject();
        json.name("content").value(content);
        if (!config.username().isBlank()) {
            json.name("username").value(config.username());
        }
        json.endObject();
        return writer.toString();
    }
}
