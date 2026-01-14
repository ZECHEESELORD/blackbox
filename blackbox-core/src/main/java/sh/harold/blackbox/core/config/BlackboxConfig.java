package sh.harold.blackbox.core.config;

import java.time.Duration;
import java.util.Objects;
import sh.harold.blackbox.core.capture.CapturePolicy;
import sh.harold.blackbox.core.notify.discord.DiscordWebhookConfig;
import sh.harold.blackbox.core.trigger.TriggerPolicy;

/**
 * Parsed configuration for Blackbox.
 */
public record BlackboxConfig(
    Duration jfrMaxAge,
    long jfrMaxSizeBytes,
    String jfrRecordingName,
    TriggerPolicy triggerPolicy,
    CapturePolicy capturePolicy,
    DiscordWebhookConfig discordWebhook,
    boolean webEnabled
) {
    public BlackboxConfig {
        Objects.requireNonNull(jfrMaxAge, "jfrMaxAge");
        Objects.requireNonNull(jfrRecordingName, "jfrRecordingName");
        Objects.requireNonNull(triggerPolicy, "triggerPolicy");
        Objects.requireNonNull(capturePolicy, "capturePolicy");
        Objects.requireNonNull(discordWebhook, "discordWebhook");
        if (jfrMaxAge.isNegative() || jfrMaxAge.isZero()) {
            throw new IllegalArgumentException("jfrMaxAge must be > 0.");
        }
        if (jfrMaxSizeBytes <= 0) {
            throw new IllegalArgumentException("jfrMaxSizeBytes must be > 0.");
        }
        if (jfrRecordingName.isBlank()) {
            throw new IllegalArgumentException("jfrRecordingName must be non-blank.");
        }
    }
}
