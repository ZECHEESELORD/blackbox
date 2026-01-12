package sh.harold.blackbox.core.trigger;

/**
 * Decision returned by the trigger engine.
 */
public enum TriggerDecision {
    ACCEPT,
    COOLDOWN,
    DEBOUNCE
}
