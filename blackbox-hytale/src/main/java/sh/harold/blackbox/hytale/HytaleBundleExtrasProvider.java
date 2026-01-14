package sh.harold.blackbox.hytale;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import sh.harold.blackbox.core.bundle.BundleAttachment;
import sh.harold.blackbox.core.capture.BundleExtrasProvider;
import sh.harold.blackbox.core.incident.IncidentReport;
import sh.harold.blackbox.core.trigger.TriggerEvent;

final class HytaleBundleExtrasProvider implements BundleExtrasProvider {
    private final System.Logger logger;

    HytaleBundleExtrasProvider(System.Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public List<BundleAttachment> extras(IncidentReport report, TriggerEvent triggerEvent) {
        List<BundleAttachment> extras = new ArrayList<>();

        addText(extras, "extras/server.txt", this::buildServerText);
        addText(extras, "extras/plugins.txt", this::buildPluginsText);
        addText(extras, "extras/worlds.txt", this::buildWorldsText);

        return extras;
    }

    private void addText(List<BundleAttachment> extras, String pathInZip, TextSupplier supplier) {
        try {
            String text = supplier.get();
            if (text == null || text.isBlank()) {
                return;
            }
            extras.add(new BundleAttachment(pathInZip, text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Failed to build bundle attachment: " + pathInZip, e);
        }
    }

    private String buildServerText() {
        StringBuilder out = new StringBuilder(256);
        try {
            HytaleServer server = HytaleServer.get();
            out.append("serverName=").append(server.getServerName()).append('\n');
        } catch (Exception e) {
            out.append("serverName=<unavailable>\n");
        }

        out.append("configVersion=").append(HytaleServerConfig.VERSION).append('\n');

        boolean nitradoWebServerPresent = false;
        try {
            PluginIdentifier id = new PluginIdentifier("Nitrado", "WebServer");
            nitradoWebServerPresent = PluginManager.get().getPlugin(id) != null;
        } catch (Exception e) {
            // ignore
        }
        out.append("nitradoWebServerPresent=").append(nitradoWebServerPresent).append('\n');

        return out.toString();
    }

    private String buildPluginsText() {
        StringBuilder out = new StringBuilder(1024);
        List<PluginBase> plugins;
        try {
            plugins = PluginManager.get().getPlugins();
        } catch (Exception e) {
            return "plugins=<unavailable>\n";
        }

        out.append("count=").append(plugins.size()).append('\n');
        for (PluginBase plugin : plugins) {
            try {
                out.append(plugin.getIdentifier()).append('\t');
                out.append(plugin.getManifest().getVersion()).append('\t');
                out.append(plugin.getName()).append('\n');
            } catch (Exception e) {
                // best-effort
            }
        }
        return out.toString();
    }

    private String buildWorldsText() {
        StringBuilder out = new StringBuilder(1024);
        Universe universe;
        try {
            universe = Universe.get();
        } catch (Exception e) {
            return "worlds=<unavailable>\n";
        }

        Map<String, World> worlds;
        try {
            worlds = universe.getWorlds();
        } catch (Exception e) {
            return "worlds=<unavailable>\n";
        }

        out.append("count=").append(worlds.size()).append('\n');
        for (Map.Entry<String, World> entry : worlds.entrySet()) {
            String key = entry.getKey();
            World world = entry.getValue();
            if (world == null) {
                continue;
            }
            out.append(key).append('\t');
            try {
                out.append(world.getName());
            } catch (Exception e) {
                out.append("<name unavailable>");
            }
            out.append('\t');
            try {
                out.append("players=").append(world.getPlayerCount());
            } catch (Exception e) {
                out.append("players=<unavailable>");
            }
            out.append('\n');
        }
        return out.toString();
    }

    @FunctionalInterface
    private interface TextSupplier {
        String get() throws Exception;
    }
}

