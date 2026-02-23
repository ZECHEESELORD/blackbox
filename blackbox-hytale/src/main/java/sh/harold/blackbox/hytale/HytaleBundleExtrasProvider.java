package sh.harold.blackbox.hytale;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import sh.harold.blackbox.core.bundle.BundleAttachment;
import sh.harold.blackbox.core.capture.BundleExtrasProvider;
import sh.harold.blackbox.core.env.ThreadDumper;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.trigger.TriggerEvent;
import sh.harold.blackbox.core.trigger.heartbeat.HeartbeatRegistry;

final class HytaleBundleExtrasProvider implements BundleExtrasProvider {
    private static final System.Logger LOGGER = System.getLogger(HytaleBundleExtrasProvider.class.getName());
    private static final String NITRADO_WEB_SERVER_CLASS = "de.nitrado.hytale.NitradoWebServer";
    private static final int MAX_LOG_TAIL_BYTES = 256 * 1024;

    private final HeartbeatRegistry heartbeatRegistry;
    private final int logTailLines;

    HytaleBundleExtrasProvider(HeartbeatRegistry heartbeatRegistry, int logTailLines) {
        this.heartbeatRegistry = heartbeatRegistry;
        this.logTailLines = logTailLines;
    }

    @Override
    public List<BundleAttachment> extras(IncidentReport report, TriggerEvent triggerEvent) {
        List<BundleAttachment> extras = new ArrayList<>();

        extras.add(new BundleAttachment("extras/server.txt",
            buildServerText().getBytes(StandardCharsets.UTF_8)));
        extras.add(new BundleAttachment("extras/plugins.txt",
            buildPluginsText().getBytes(StandardCharsets.UTF_8)));
        extras.add(new BundleAttachment("extras/worlds.txt",
            buildWorldsText().getBytes(StandardCharsets.UTF_8)));
        extras.add(new BundleAttachment("extras/heartbeats.txt",
            buildHeartbeatsText().getBytes(StandardCharsets.UTF_8)));
        extras.add(new BundleAttachment("extras/threads.txt",
            ThreadDumper.dump().getBytes(StandardCharsets.UTF_8)));

        if (logTailLines > 0) {
            String logTail = buildServerLogTail();
            if (!logTail.isEmpty()) {
                extras.add(new BundleAttachment("extras/server-log.txt",
                    logTail.getBytes(StandardCharsets.UTF_8)));
            }
        }

        return extras;
    }

    private String buildServerText() {
        StringBuilder out = new StringBuilder(256);
        try {
            HytaleServer server = HytaleServer.get();
            out.append("server.name=").append(server.getServerName()).append('\n');
        } catch (Exception e) {
            out.append("server.name=<unavailable>\n");
        }
        try {
            out.append("hytale.version=").append(HytaleServer.class
                .getPackage().getImplementationVersion()).append('\n');
        } catch (Exception e) {
            out.append("hytale.version=<unavailable>\n");
        }
        boolean hasNitrado = isClassPresent(NITRADO_WEB_SERVER_CLASS, HytaleBundleExtrasProvider.class.getClassLoader());
        out.append("nitrado.present=").append(hasNitrado).append('\n');
        return out.toString();
    }

    private String buildPluginsText() {
        StringBuilder out = new StringBuilder(512);
        try {
            List<PluginBase> plugins = PluginManager.get().getPlugins();
            out.append("count=").append(plugins.size()).append('\n');
            for (PluginBase plugin : plugins) {
                try {
                    out.append(plugin.getIdentifier())
                        .append('\t').append(plugin.getManifest().getVersion())
                        .append('\t').append(plugin.getName())
                        .append('\n');
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            out.append("<unavailable>\n");
        }
        return out.toString();
    }

    private String buildWorldsText() {
        StringBuilder out = new StringBuilder(256);
        try {
            World defaultWorld = Universe.get().getDefaultWorld();
            if (defaultWorld != null) {
                out.append(defaultWorld.getName())
                    .append('\t').append("players=").append(defaultWorld.getPlayerCount())
                    .append('\n');
            } else {
                out.append("<no default world>\n");
            }
        } catch (Exception e) {
            out.append("<unavailable>\n");
        }
        return out.toString();
    }

    private String buildHeartbeatsText() {
        StringBuilder out = new StringBuilder(256);
        Map<String, Instant> snapshot = heartbeatRegistry.snapshot();
        new TreeMap<>(snapshot).forEach((scope, lastBeat) ->
            out.append(scope).append('\t').append(lastBeat).append('\n')
        );
        if (snapshot.isEmpty()) {
            out.append("<no heartbeats recorded>\n");
        }
        return out.toString();
    }

    private String buildServerLogTail() {
        Path logsDir = Paths.get("logs");
        if (!Files.isDirectory(logsDir)) {
            return "";
        }
        try (Stream<Path> logFiles = Files.list(logsDir)) {
            Path latestLog = logFiles
                .filter(p -> p.getFileName().toString().endsWith(".log"))
                .max(Comparator.comparingLong(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }))
                .orElse(null);

            if (latestLog == null) {
                return "";
            }

            return readTail(latestLog, logTailLines);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to read server log tail.", e);
            return "";
        }
    }

    static String readTail(Path file, int lines) throws IOException {
        return readTail(file, lines, MAX_LOG_TAIL_BYTES);
    }

    static String readTail(Path file, int lines, int maxBytes) throws IOException {
        if (lines <= 0 || maxBytes <= 0) {
            return "";
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                return "";
            }

            int newlineCount = 0;
            long pos = length - 1;

            raf.seek(pos);
            if (raf.readByte() == '\n') {
                pos--;
            }

            while (pos >= 0) {
                raf.seek(pos);
                if (raf.readByte() == '\n') {
                    newlineCount++;
                    if (newlineCount >= lines) {
                        pos++;
                        break;
                    }
                }
                pos--;
            }

            long lineBoundStart = Math.max(pos, 0);
            long byteBoundStart = Math.max(0, length - maxBytes);
            long startPos = Math.max(lineBoundStart, byteBoundStart);
            int tailLength = (int) (length - startPos);
            byte[] tailBytes = new byte[tailLength];
            raf.seek(startPos);
            raf.readFully(tailBytes);
            return new String(tailBytes, StandardCharsets.UTF_8);
        }
    }

    static boolean isClassPresent(String className, ClassLoader classLoader) {
        if (className == null || className.isBlank()) {
            return false;
        }
        try {
            // false => link/load only, do not run static initializers.
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            LOGGER.log(System.Logger.Level.WARNING, "Class presence check failed for '" + className + "'.", t);
            return false;
        }
    }
}
