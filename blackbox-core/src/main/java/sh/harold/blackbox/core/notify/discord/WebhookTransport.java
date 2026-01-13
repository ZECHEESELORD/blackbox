package sh.harold.blackbox.core.notify.discord;

import java.net.URI;

/**
 * Transport hook for webhook delivery.
 */
@FunctionalInterface
public interface WebhookTransport {
    void post(URI uri, String jsonPayload) throws Exception;
}
