package sh.harold.blackbox.core.jfr;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * Lightweight marker event for verifying JFR captures in tests.
 */
@Name("sh.harold.blackbox.marker")
public final class BlackboxMarkerEvent extends Event {
    @Label("Message")
    public String message;
}
