package sh.harold.blackbox.core.capture;

import java.util.Objects;
import sh.harold.blackbox.core.retention.RetentionPolicy;

/**
 * Capture policy container.
 */
public record CapturePolicy(RetentionPolicy retention) {
    public CapturePolicy {
        Objects.requireNonNull(retention, "retention");
    }
}
