package sh.harold.blackbox.core.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import sh.harold.blackbox.core.env.EnvCollector;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.json.IncidentJson;
import sh.harold.blackbox.core.report.ReportHtml;

/**
 * Builds deterministic incident bundles.
 */
public final class BundleBuilder {
    private final Clock clock;
    private final System.Logger logger;

    public BundleBuilder(Clock clock) {
        this(clock, System.getLogger(BundleBuilder.class.getName()));
    }

    public BundleBuilder(Clock clock, System.Logger logger) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Path build(
        IncidentReport report,
        Path recordingJfr,
        Path outputZip,
        List<BundleAttachment> extras
    ) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(recordingJfr, "recordingJfr");
        Objects.requireNonNull(outputZip, "outputZip");

        List<BundleAttachment> sortedExtras = new ArrayList<>(extras == null ? List.of() : extras);
        sortedExtras.sort(Comparator.comparing(BundleAttachment::pathInZip));

        Path parent = outputZip.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            writeIncidentJson(report, zip);
            writeReportHtml(report, zip);
            writeRecording(recordingJfr, zip);
            writeEnvFiles(zip);
            writeExtras(sortedExtras, zip);
        }

        return outputZip;
    }

    private void writeIncidentJson(IncidentReport report, ZipOutputStream zip) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        IncidentJson.write(report, buffer);
        ZipEntry entry = zipEntry("incident.json");
        zip.putNextEntry(entry);
        zip.write(buffer.toByteArray());
        zip.closeEntry();
    }

    private void writeRecording(Path recordingJfr, ZipOutputStream zip) throws IOException {
        ZipEntry entry = zipEntry("recording.jfr");
        zip.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(recordingJfr)) {
            in.transferTo(zip);
        }
        zip.closeEntry();
    }

    private void writeReportHtml(IncidentReport report, ZipOutputStream zip) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ReportHtml.write(report, buffer);
        ZipEntry entry = zipEntry("report.html");
        zip.putNextEntry(entry);
        zip.write(buffer.toByteArray());
        zip.closeEntry();
    }

    private void writeEnvFiles(ZipOutputStream zip) throws IOException {
        ZipEntry jvm = zipEntry("env/jvm.txt");
        zip.putNextEntry(jvm);
        zip.write(EnvCollector.jvmInfo().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        ZipEntry os = zipEntry("env/os.txt");
        zip.putNextEntry(os);
        zip.write(EnvCollector.osInfo().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void writeExtras(List<BundleAttachment> extras, ZipOutputStream zip) throws IOException {
        for (BundleAttachment extra : extras) {
            ZipEntry entry = zipEntry(extra.pathInZip());
            zip.putNextEntry(entry);
            zip.write(extra.data());
            zip.closeEntry();
        }
    }

    private static ZipEntry zipEntry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        return entry;
    }
}
