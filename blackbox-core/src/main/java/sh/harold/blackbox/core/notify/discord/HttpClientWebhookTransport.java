package sh.harold.blackbox.core.notify.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Async HTTP transport for Discord webhook delivery.
 */
public final class HttpClientWebhookTransport implements WebhookTransport {
    private final HttpClient client;
    private final Duration requestTimeout;
    private final System.Logger logger;

    public HttpClientWebhookTransport(Duration requestTimeout) {
        this(HttpClient.newHttpClient(), requestTimeout,
            System.getLogger(HttpClientWebhookTransport.class.getName()));
    }

    public HttpClientWebhookTransport(HttpClient client, Duration requestTimeout, System.Logger logger) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void post(URI uri, String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    logger.log(System.Logger.Level.WARNING, "Discord webhook request failed.", ex);
                    return null;
                });
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Failed to send Discord webhook.", e);
        }
    }
}
