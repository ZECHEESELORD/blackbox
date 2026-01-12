package sh.harold.blackbox.core.json;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;

/**
 * Deterministic JSON writer for incident reports.
 *
 * Field order:
 * - meta: id, createdAt, severity, trigger, world, headline
 * - summary: likelyCause, whatHappened, nextSteps
 */
public final class IncidentJson {
    private IncidentJson() {
    }

    public static void write(IncidentReport report, OutputStream out) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(out, "out");

        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        JsonWriter json = new JsonWriter(writer);
        json.beginObject();
        writeMeta(json, report.meta());
        writeSummary(json, report.summary());
        json.endObject();
        writer.flush();
    }

    private static void writeMeta(JsonWriter json, IncidentMetadata meta) throws IOException {
        json.name("meta").beginObject();
        json.name("id").value(meta.id().value());
        json.name("createdAt").value(meta.createdAt().toString());
        json.name("severity").value(meta.severity().name());
        json.name("trigger").value(meta.trigger());
        json.name("world");
        if (meta.world() == null) {
            json.nullValue();
        } else {
            json.value(meta.world());
        }
        json.name("headline").value(meta.headline());
        json.endObject();
    }

    private static void writeSummary(JsonWriter json, IncidentSummary summary) throws IOException {
        json.name("summary").beginObject();
        json.name("likelyCause").value(summary.likelyCause());
        json.name("whatHappened").beginArray();
        for (String item : summary.whatHappened()) {
            json.value(item);
        }
        json.endArray();
        json.name("nextSteps").beginArray();
        for (String item : summary.nextSteps()) {
            json.value(item);
        }
        json.endArray();
        json.endObject();
    }
}
