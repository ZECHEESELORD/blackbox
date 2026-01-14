package sh.harold.blackbox.core.capture;

import java.util.List;
import sh.harold.blackbox.core.bundle.BundleAttachment;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.trigger.TriggerEvent;

/**
 * Supplies platform-specific attachments to include in incident bundles.
 */
@FunctionalInterface
public interface BundleExtrasProvider {
    List<BundleAttachment> extras(IncidentReport report, TriggerEvent triggerEvent) throws Exception;

    static BundleExtrasProvider none() {
        return (report, triggerEvent) -> List.of();
    }
}

