package sh.harold.blackbox.hytale;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import sh.harold.blackbox.core.bundle.BundleBuilder;
import sh.harold.blackbox.core.capture.BundleExtrasProvider;
import sh.harold.blackbox.core.capture.CapturePipeline;
import sh.harold.blackbox.core.capture.IncidentNotifier;
import sh.harold.blackbox.core.capture.RecordingDumper;
import sh.harold.blackbox.core.config.BlackboxConfig;
import sh.harold.blackbox.core.config.BlackboxConfigLoader;
import sh.harold.blackbox.core.jfr.JfrController;
import sh.harold.blackbox.core.notify.discord.DiscordWebhookNotifier;
import sh.harold.blackbox.core.notify.discord.HttpClientWebhookTransport;
import sh.harold.blackbox.core.retention.FileDeleter;
import sh.harold.blackbox.core.retention.RetentionManager;
import sh.harold.blackbox.core.trigger.TriggerEvent;
import sh.harold.blackbox.core.trigger.TriggerKind;
import sh.harold.blackbox.core.trigger.TriggerEngine;
import sh.harold.blackbox.core.trigger.heartbeat.HeartbeatRegistry;
import sh.harold.blackbox.core.trigger.heartbeat.HeartbeatStallDetector;

final class BlackboxRuntime implements AutoCloseable {
    private final BlackboxPlugin plugin;
    private final Clock clock;
    private final System.Logger logger;
    private final BlackboxConfig config;
    private final Path dataDir;
    private final Path configPath;
    private final Path incidentDir;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService worker;

    private final JfrController jfr;
    private final HeartbeatRegistry heartbeatRegistry;
    private final HeartbeatStallDetector stallDetector;
    private final TriggerEngine triggerEngine;
    private final CapturePipeline capturePipeline;

    private final AtomicBoolean stallCheckRunning = new AtomicBoolean(false);
    private final Map<String, AtomicBoolean> heartbeatPending = new ConcurrentHashMap<>();
    private int heartbeatSweepCounter;
    private final AtomicReference<Instant> lastIncidentAt = new AtomicReference<>();
    private final AtomicReference<String> lastIncidentId = new AtomicReference<>();

    static BlackboxRuntime start(BlackboxPlugin plugin) throws Exception {
        Objects.requireNonNull(plugin, "plugin");
        System.Logger logger = System.getLogger(BlackboxRuntime.class.getName());

        Path dataDir = plugin.getDataDirectory();
        Path configPath = dataDir.resolve("blackbox.properties");
        BlackboxConfig config = BlackboxConfigLoader.loadOrCreate(configPath, logger);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("blackbox-scheduler")
        );
        ExecutorService worker = Executors.newSingleThreadExecutor(new NamedThreadFactory("blackbox-worker"));

        Clock clock = Clock.systemUTC();

        JfrController jfr = new JfrController(config.jfrMaxAge(), config.jfrMaxSizeBytes(), config.jfrRecordingName());
        jfr.start();

        HeartbeatRegistry heartbeatRegistry = new HeartbeatRegistry(clock);
        HeartbeatStallDetector stallDetector = new HeartbeatStallDetector(
            clock,
            heartbeatRegistry,
            config.triggerPolicy().stallDegradedMs()
        );

        TriggerEngine triggerEngine = new TriggerEngine(clock, config.triggerPolicy());

        RecordingDumper dumper = (target) -> {
            jfr.dump(target);
            return target;
        };

        IncidentNotifier notifier = buildNotifier(clock, config, logger, worker);

        Path incidentDir = dataDir.resolve("incidents");
        Path tempDir = dataDir.resolve("temp");

        CapturePipeline capturePipeline = new CapturePipeline(
            clock,
            triggerEngine,
            dumper,
            new BundleBuilder(clock, logger),
            new RetentionManager(clock, logger, FileDeleter.defaultDeleter()),
            notifier,
            new HytaleBundleExtrasProvider(logger),
            incidentDir,
            tempDir,
            config.capturePolicy(),
            logger
        );

        BlackboxRuntime runtime = new BlackboxRuntime(
            plugin,
            clock,
            logger,
            config,
            dataDir,
            configPath,
            incidentDir,
            scheduler,
            worker,
            jfr,
            heartbeatRegistry,
            stallDetector,
            triggerEngine,
            capturePipeline
        );
        runtime.startScheduledWork();
        runtime.registerCommands();
        runtime.logStartup();
        return runtime;
    }

    private static IncidentNotifier buildNotifier(
        Clock clock,
        BlackboxConfig config,
        System.Logger logger,
        ExecutorService worker
    ) {
        if (config.discordWebhook().webhookUrl().isBlank()) {
            return IncidentNotifier.noop();
        }
        return new DiscordWebhookNotifier(
            clock,
            logger,
            config.discordWebhook(),
            new HttpClientWebhookTransport(config.discordWebhook().requestTimeout()),
            worker
        );
    }

    private BlackboxRuntime(
        BlackboxPlugin plugin,
        Clock clock,
        System.Logger logger,
        BlackboxConfig config,
        Path dataDir,
        Path configPath,
        Path incidentDir,
        ScheduledExecutorService scheduler,
        ExecutorService worker,
        JfrController jfr,
        HeartbeatRegistry heartbeatRegistry,
        HeartbeatStallDetector stallDetector,
        TriggerEngine triggerEngine,
        CapturePipeline capturePipeline
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.logger = logger;
        this.config = config;
        this.dataDir = dataDir;
        this.configPath = configPath;
        this.incidentDir = incidentDir;
        this.scheduler = scheduler;
        this.worker = worker;
        this.jfr = jfr;
        this.heartbeatRegistry = heartbeatRegistry;
        this.stallDetector = stallDetector;
        this.triggerEngine = triggerEngine;
        this.capturePipeline = capturePipeline;
    }

    void registerCommands() {
        try {
            BlackboxCommand command = new BlackboxCommand(this);
            command.setOwner(plugin);
            plugin.getCommandRegistry().registerCommand(command);
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Failed to register /blackbox command.", e);
        }
    }

    Optional<String> captureManual() {
        TriggerEvent event = new TriggerEvent(TriggerKind.MANUAL, "server", clock.instant(), java.util.Map.of());
        return capture(event);
    }

    Optional<String> capture(TriggerEvent event) {
        Optional<String> id;
        try {
            id = capturePipeline.handle(event).map(incidentId -> incidentId.value());
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Capture pipeline threw unexpectedly.", e);
            return Optional.empty();
        }

        if (id.isPresent()) {
            lastIncidentAt.set(event.at());
            lastIncidentId.set(id.get());
        }
        return id;
    }

    Path incidentDir() {
        return incidentDir;
    }

    Path dataDir() {
        return dataDir;
    }

    Path configPath() {
        return configPath;
    }

    BlackboxConfig config() {
        return config;
    }

    ExecutorService worker() {
        return worker;
    }

    Optional<String> lastIncidentId() {
        return Optional.ofNullable(lastIncidentId.get());
    }

    Optional<Instant> lastIncidentAt() {
        return Optional.ofNullable(lastIncidentAt.get());
    }

    private void logStartup() {
        try {
            plugin.getLogger().at(Level.INFO).log("Blackbox started.");
            plugin.getLogger().at(Level.INFO).log("Config: %s", configPath);
        } catch (Exception e) {
            logger.log(System.Logger.Level.INFO, "Blackbox started.");
        }
    }

    private void startScheduledWork() {
        Universe universe;
        try {
            universe = Universe.get();
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Universe.get() failed; heartbeats disabled until restart.", e);
            return;
        }

        universe.getUniverseReady().thenRun(() -> {
            scheduler.scheduleAtFixedRate(
                this::tickHeartbeats,
                0L,
                50L,
                TimeUnit.MILLISECONDS
            );
            scheduler.scheduleAtFixedRate(
                this::scheduleStallCheck,
                250L,
                250L,
                TimeUnit.MILLISECONDS
            );
        });
    }

    private void tickHeartbeats() {
        try {
            Universe universe = Universe.get();
            Map<String, World> worlds = universe.getWorlds();
            for (Map.Entry<String, World> entry : worlds.entrySet()) {
                String scope = entry.getKey();
                if (scope == null || scope.isBlank()) {
                    continue;
                }
                World world = entry.getValue();
                if (world == null) {
                    continue;
                }

                AtomicBoolean pending = heartbeatPending.computeIfAbsent(scope, ignored -> new AtomicBoolean(false));
                if (!pending.compareAndSet(false, true)) {
                    continue;
                }

                world.execute(() -> {
                    try {
                        heartbeatRegistry.beat(scope);
                    } catch (Exception e) {
                        logger.log(System.Logger.Level.WARNING, "Failed to beat heartbeat for " + scope, e);
                    } finally {
                        pending.set(false);
                    }
                });
            }

            heartbeatSweepCounter++;
            if (heartbeatSweepCounter >= 100) {
                heartbeatSweepCounter = 0;
                heartbeatPending.keySet().removeIf(scope -> !worlds.containsKey(scope));
            }
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Heartbeat tick failed.", e);
        }
    }

    private void scheduleStallCheck() {
        if (!stallCheckRunning.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                for (TriggerEvent event : stallDetector.check()) {
                    capture(event);
                }
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING, "Stall check failed.", e);
            } finally {
                stallCheckRunning.set(false);
            }
        });
    }

    @Override
    public void close() {
        try {
            scheduler.shutdownNow();
            worker.shutdownNow();
            worker.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "Executor shutdown failed.", e);
        }

        try {
            jfr.close();
        } catch (Exception e) {
            logger.log(System.Logger.Level.WARNING, "JFR shutdown failed.", e);
        }
    }
}
