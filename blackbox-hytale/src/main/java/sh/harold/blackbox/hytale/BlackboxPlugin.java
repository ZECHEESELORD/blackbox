package sh.harold.blackbox.hytale;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.Objects;

public final class BlackboxPlugin extends JavaPlugin {
    private final System.Logger logger = System.getLogger(BlackboxPlugin.class.getName());
    private BlackboxRuntime runtime;

    public BlackboxPlugin(JavaPluginInit init) {
        super(Objects.requireNonNull(init, "init"));
    }

    @Override
    protected void start() {
        try {
            runtime = BlackboxRuntime.start(this);
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "Blackbox failed to start.", e);
        }
    }

    @Override
    protected void shutdown() {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Blackbox shutdown failed.", e);
        } finally {
            runtime = null;
        }
    }
}

