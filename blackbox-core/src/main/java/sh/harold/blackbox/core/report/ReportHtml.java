package sh.harold.blackbox.core.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;

/**
 * Renders a self-contained incident report HTML page.
 */
public final class ReportHtml {
    private ReportHtml() {
    }

    public static void write(IncidentReport report, OutputStream out) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(out, "out");

        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(render(report));
        writer.flush();
    }

    private static String render(IncidentReport report) {
        IncidentMetadata meta = report.meta();
        IncidentSummary summary = report.summary();

        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
            .append("<title>Blackbox Incident Report</title>")
            .append("<style>")
            .append(":root{--bg:#f4f1ea;--fg:#1f1a13;--muted:#6b5f52;--card:#ffffff;")
            .append("--accent:#7c4d2a;--border:#e5ded3;}")
            .append("body{margin:0;font-family:\"Georgia\",\"Times New Roman\",serif;background:var(--bg);")
            .append("color:var(--fg);line-height:1.55;}")
            .append("main{max-width:900px;margin:32px auto;padding:24px;}")
            .append("header{background:var(--card);border:1px solid var(--border);")
            .append("border-radius:16px;padding:24px;box-shadow:0 6px 18px rgba(0,0,0,0.05);}")
            .append("h1{margin:0 0 8px;font-size:32px;}")
            .append("h2{margin:28px 0 12px;font-size:20px;color:var(--accent);}")
            .append(".meta{color:var(--muted);font-size:14px;}")
            .append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;}")
            .append(".card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:16px;}")
            .append("ul{margin:8px 0 0 18px;padding:0;}")
            .append("code{background:#efe8dc;padding:2px 6px;border-radius:6px;}")
            .append("</style></head><body><main>");

        html.append("<header><div class=\"meta\">Incident ID: ")
            .append(escapeHtml(meta.id().value()))
            .append("</div>")
            .append("<h1>")
            .append(escapeHtmlWithBreaks(meta.headline()))
            .append("</h1>")
            .append("<div class=\"meta\">Severity: ")
            .append(escapeHtml(meta.severity().name()))
            .append(" · Trigger: ")
            .append(escapeHtml(meta.trigger()))
            .append(" · Created: ")
            .append(escapeHtml(meta.createdAt().toString()))
            .append("</div>");

        if (meta.world() != null) {
            html.append("<div class=\"meta\">World: ")
                .append(escapeHtml(meta.world()))
                .append("</div>");
        }
        html.append("</header>");

        html.append("<section class=\"grid\">")
            .append("<div class=\"card\"><h2>Likely cause</h2><p>")
            .append(escapeHtmlWithBreaks(summary.likelyCause()))
            .append("</p></div>")
            .append("<div class=\"card\"><h2>Next steps</h2>")
            .append(renderList(summary.nextSteps()))
            .append("</div>")
            .append("</section>");

        html.append("<section class=\"card\"><h2>What happened</h2>")
            .append(renderList(summary.whatHappened()))
            .append("</section>");

        html.append("<section class=\"card\"><h2>How to open recording.jfr</h2>")
            .append("<p>Use <code>JDK Mission Control</code> to open <code>recording.jfr</code>. ")
            .append("Open Mission Control, choose File &rarr; Open, and select the recording.</p>")
            .append("</section>");

        html.append("</main></body></html>");
        return html.toString();
    }

    private static String renderList(Iterable<String> items) {
        StringBuilder html = new StringBuilder();
        html.append("<ul>");
        for (String item : items) {
            html.append("<li>").append(escapeHtmlWithBreaks(item)).append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    private static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static String escapeHtmlWithBreaks(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r') {
                if (i + 1 < value.length() && value.charAt(i + 1) == '\n') {
                    i++;
                }
                escaped.append("<br>");
                continue;
            }
            if (c == '\n') {
                escaped.append("<br>");
                continue;
            }
            if (c == '\t') {
                escaped.append("&#9;");
                continue;
            }
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
