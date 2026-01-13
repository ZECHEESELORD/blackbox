package sh.harold.blackbox.core.notify.discord;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for Discord webhook notifications.
 */
public record DiscordWebhookConfig(
    String webhookUrl,
    Duration cooldown,
    Duration requestTimeout,
    String username
) {
    public DiscordWebhookConfig {
        Objects.requireNonNull(webhookUrl, "webhookUrl");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(username, "username");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must be non-negative.");
        }
        if (requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be non-negative.");
        }
    }
}
