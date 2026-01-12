package sh.harold.blackbox.core.bundle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.harold.blackbox.core.incident.IncidentId;
import sh.harold.blackbox.core.incident.IncidentMetadata;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.incident.IncidentSummary;
import sh.harold.blackbox.core.incident.Severity;

class BundleReportTest {

    @Test
    void bundleContainsReportHtml(@TempDir Path tempDir) throws Exception {
        Path recording = tempDir.resolve("recording.jfr");
        Files.write(recording, new byte[] {1, 2, 3});

        Clock clock = Clock.fixed(Instant.parse("2026-01-11T02:00:00Z"), ZoneOffset.UTC);
        IncidentReport report = reportWithEscapes(clock);

        Path outputZip = tempDir.resolve("incident.zip");
        new BundleBuilder(clock).build(report, recording, outputZip, List.of());

        try (ZipFile zip = new ZipFile(outputZip.toFile())) {
            ZipEntry reportEntry = zip.getEntry("report.html");
            assertNotNull(reportEntry);

            String html = new String(zip.getInputStream(reportEntry).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("Incident ID"));
            assertTrue(html.contains("Severity"));
            assertTrue(html.contains("Likely cause"));
            assertTrue(html.contains("What happened"));
            assertTrue(html.contains("Next steps"));
            assertTrue(html.contains("How to open recording.jfr"));
            assertTrue(html.contains("world-one"));
            assertTrue(html.contains("2026-01-11T02:00:00Z"));
            assertTrue(html.contains("Trigger:"));

            assertTrue(html.contains("Quote &quot;here&quot;"));
            assertTrue(html.contains("C:\\temp\\file"));
            assertTrue(html.contains("Line one<br>Line two"));
            assertTrue(html.contains("tab&#9;value"));

            assertFalse(html.contains("http://"));
            assertFalse(html.contains("https://"));
            assertFalse(html.contains("<script src="));
        }
    }

    private static IncidentReport reportWithEscapes(Clock clock) {
        IncidentMetadata meta = new IncidentMetadata(
            new IncidentId("20260111-020000.000Z-abcdef"),
            clock.instant(),
            Severity.DEGRADED,
            "Trigger: \"quoted\" and path C:\\temp\\file",
            "world-one",
            "Quote \"here\" and backslash C:\\temp\\file\nLine two"
        );
        IncidentSummary summary = new IncidentSummary(
            "Likely cause with tab\tvalue",
            List.of("Line one\nLine two", "Next \"item\""),
            List.of("Restart server", "Review \\logs")
        );
        return new IncidentReport(meta, summary);
    }
}
