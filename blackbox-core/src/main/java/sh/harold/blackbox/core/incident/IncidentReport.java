package sh.harold.blackbox.core.incident;

import java.util.Objects;

/**
 * Full report data used to build an incident bundle.
 */
public record IncidentReport(IncidentMetadata meta, IncidentSummary summary) {
    public IncidentReport {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(summary, "summary");
    }
}
