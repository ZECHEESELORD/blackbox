package sh.harold.blackbox.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import sh.harold.blackbox.core.capture.CapturePolicy;
import sh.harold.blackbox.core.config.BlackboxConfig;
import sh.harold.blackbox.core.notify.discord.DiscordWebhookConfig;
import sh.harold.blackbox.core.retention.RetentionPolicy;
import sh.harold.blackbox.core.trigger.TriggerPolicy;

/**
 * Loads {@link BlackboxConfig} via Hytale's built-in {@link Config} system.
 *
 * <p>Stored at {@code <pluginDataDir>/blackbox.json}.
 */
final class HytaleBlackboxConfig {
    private static final String FILE_NAME = "blackbox";

    private static final Duration DEFAULT_JFR_MAX_AGE = Duration.ofMinutes(15);
    private static final long DEFAULT_JFR_MAX_SIZE_BYTES = 256L * 1024L * 1024L;
    private static final String DEFAULT_JFR_RECORDING_NAME = "blackbox";

    private static final Duration DEFAULT_TRIGGER_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration DEFAULT_TRIGGER_DEBOUNCE = Duration.ofSeconds(2);
    private static final long DEFAULT_STALL_DEGRADED_MS = 2_000L;
    private static final long DEFAULT_STALL_CRITICAL_MS = 10_000L;

    private static final int DEFAULT_RETENTION_MAX_COUNT = 25;
    private static final long DEFAULT_RETENTION_MAX_TOTAL_BYTES = 1024L * 1024L * 1024L;
    private static final Duration DEFAULT_RETENTION_MAX_AGE = Duration.ofDays(7);

    private static final String DEFAULT_DISCORD_WEBHOOK_URL = "";
    private static final Duration DEFAULT_DISCORD_COOLDOWN = Duration.ofMinutes(1);
    private static final Duration DEFAULT_DISCORD_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_DISCORD_USERNAME = "Blackbox";

    private static final boolean DEFAULT_WEB_ENABLED = false;

    private HytaleBlackboxConfig() {
    }

    static Path path(Path dataDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        return dataDir.resolve(FILE_NAME + ".json");
    }

    static BlackboxConfig loadOrCreate(Path dataDir, System.Logger logger) {
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(logger, "logger");

        Path configPath = path(dataDir);
        boolean existed;
        try {
            existed = Files.exists(configPath);
        } catch (Exception e) {
            existed = false;
        }

        Config<FileConfig> configFile = new Config<>(dataDir, FILE_NAME, FileConfig.CODEC);

        FileConfig raw;
        try {
            raw = configFile.load().join();
        } catch (Exception e) {
            logger.log(
                System.Logger.Level.WARNING,
                "Failed to load Blackbox config from " + configPath + "; using defaults.",
                e
            );
            raw = new FileConfig();
        }

        if (!existed) {
            configFile.save().exceptionally(ex -> {
                logger.log(System.Logger.Level.WARNING, "Failed to write default Blackbox config to " + configPath, ex);
                return null;
            });
            logger.log(System.Logger.Level.INFO, "Wrote default config: " + configPath);
        }

        return raw.toCoreConfig(logger);
    }

    private static BlackboxConfig defaultCoreConfig() {
        TriggerPolicy triggerPolicy = new TriggerPolicy(
            DEFAULT_TRIGGER_COOLDOWN,
            DEFAULT_TRIGGER_DEBOUNCE,
            DEFAULT_STALL_DEGRADED_MS,
            DEFAULT_STALL_CRITICAL_MS
        );
        RetentionPolicy retentionPolicy = new RetentionPolicy(
            DEFAULT_RETENTION_MAX_COUNT,
            DEFAULT_RETENTION_MAX_TOTAL_BYTES,
            DEFAULT_RETENTION_MAX_AGE
        );
        CapturePolicy capturePolicy = new CapturePolicy(retentionPolicy);
        DiscordWebhookConfig discord = new DiscordWebhookConfig(
            DEFAULT_DISCORD_WEBHOOK_URL,
            DEFAULT_DISCORD_COOLDOWN,
            DEFAULT_DISCORD_REQUEST_TIMEOUT,
            DEFAULT_DISCORD_USERNAME
        );
        return new BlackboxConfig(
            DEFAULT_JFR_MAX_AGE,
            DEFAULT_JFR_MAX_SIZE_BYTES,
            DEFAULT_JFR_RECORDING_NAME,
            triggerPolicy,
            capturePolicy,
            discord,
            DEFAULT_WEB_ENABLED
        );
    }

    private static final class FileConfig {
        public int version = 1;
        public Jfr jfr = new Jfr();
        public Trigger trigger = new Trigger();
        public Retention retention = new Retention();
        public Discord discord = new Discord();
        public Web web = new Web();

        static final BuilderCodec<FileConfig> CODEC = BuilderCodec
            .builder(FileConfig.class, FileConfig::new)
            .addField(new KeyedCodec<>("Version", Codec.INTEGER), (c, v) -> {
                if (v != null) {
                    c.version = v;
                }
            }, c -> c.version)
            .addField(new KeyedCodec<>("Jfr", Jfr.CODEC), (c, v) -> {
                if (v != null) {
                    c.jfr = v;
                }
            }, c -> c.jfr)
            .addField(new KeyedCodec<>("Trigger", Trigger.CODEC), (c, v) -> {
                if (v != null) {
                    c.trigger = v;
                }
            }, c -> c.trigger)
            .addField(new KeyedCodec<>("Retention", Retention.CODEC), (c, v) -> {
                if (v != null) {
                    c.retention = v;
                }
            }, c -> c.retention)
            .addField(new KeyedCodec<>("Discord", Discord.CODEC), (c, v) -> {
                if (v != null) {
                    c.discord = v;
                }
            }, c -> c.discord)
            .addField(new KeyedCodec<>("Web", Web.CODEC), (c, v) -> {
                if (v != null) {
                    c.web = v;
                }
            }, c -> c.web)
            .build();

        BlackboxConfig toCoreConfig(System.Logger logger) {
            Objects.requireNonNull(logger, "logger");

            Jfr jfr = this.jfr == null ? new Jfr() : this.jfr;
            Trigger trigger = this.trigger == null ? new Trigger() : this.trigger;
            Retention retention = this.retention == null ? new Retention() : this.retention;
            Discord discord = this.discord == null ? new Discord() : this.discord;
            Web web = this.web == null ? new Web() : this.web;

            Duration jfrMaxAge = positiveDuration(jfr.maxAge, DEFAULT_JFR_MAX_AGE, "Jfr.MaxAge", logger);
            long jfrMaxSizeBytes = positiveLong(jfr.maxSizeBytes, DEFAULT_JFR_MAX_SIZE_BYTES, "Jfr.MaxSizeBytes", logger);
            String recordingName = nonBlankString(
                jfr.recordingName,
                DEFAULT_JFR_RECORDING_NAME,
                "Jfr.RecordingName",
                logger
            );

            Duration cooldown = nonNegativeDuration(
                trigger.cooldown,
                DEFAULT_TRIGGER_COOLDOWN,
                "Trigger.Cooldown",
                logger
            );
            Duration debounce = nonNegativeDuration(
                trigger.debounce,
                DEFAULT_TRIGGER_DEBOUNCE,
                "Trigger.Debounce",
                logger
            );
            long stallDegradedMs = positiveLong(
                trigger.stallDegradedMs,
                DEFAULT_STALL_DEGRADED_MS,
                "Trigger.StallDegradedMs",
                logger
            );
            long stallCriticalMs = positiveLong(
                trigger.stallCriticalMs,
                DEFAULT_STALL_CRITICAL_MS,
                "Trigger.StallCriticalMs",
                logger
            );
            if (stallCriticalMs < stallDegradedMs) {
                logger.log(
                    System.Logger.Level.WARNING,
                    "Config Trigger.StallCriticalMs (" + stallCriticalMs + ") is < Trigger.StallDegradedMs (" + stallDegradedMs
                        + "); clamping."
                );
                stallCriticalMs = stallDegradedMs;
            }

            int maxCount = nonNegativeInt(retention.maxCount, DEFAULT_RETENTION_MAX_COUNT, "Retention.MaxCount", logger);
            long maxTotalBytes = nonNegativeLong(
                retention.maxTotalBytes,
                DEFAULT_RETENTION_MAX_TOTAL_BYTES,
                "Retention.MaxTotalBytes",
                logger
            );
            Duration maxAge = retention.maxAge;
            if (maxAge != null && maxAge.isNegative()) {
                logger.log(
                    System.Logger.Level.WARNING,
                    "Config Retention.MaxAge is negative; using default " + DEFAULT_RETENTION_MAX_AGE + "."
                );
                maxAge = DEFAULT_RETENTION_MAX_AGE;
            }

            String webhookUrl = discord.webhookUrl == null ? DEFAULT_DISCORD_WEBHOOK_URL : discord.webhookUrl;
            Duration webhookCooldown = nonNegativeDuration(
                discord.cooldown,
                DEFAULT_DISCORD_COOLDOWN,
                "Discord.Cooldown",
                logger
            );
            Duration requestTimeout = nonNegativeDuration(
                discord.requestTimeout,
                DEFAULT_DISCORD_REQUEST_TIMEOUT,
                "Discord.RequestTimeout",
                logger
            );
            String username = nonBlankString(discord.username, DEFAULT_DISCORD_USERNAME, "Discord.Username", logger);

            try {
                return new BlackboxConfig(
                    jfrMaxAge,
                    jfrMaxSizeBytes,
                    recordingName,
                    new TriggerPolicy(cooldown, debounce, stallDegradedMs, stallCriticalMs),
                    new CapturePolicy(new RetentionPolicy(maxCount, maxTotalBytes, maxAge)),
                    new DiscordWebhookConfig(webhookUrl, webhookCooldown, requestTimeout, username),
                    web.enabled
                );
            } catch (RuntimeException e) {
                logger.log(System.Logger.Level.WARNING, "Invalid Blackbox config; falling back to defaults.", e);
                return defaultCoreConfig();
            }
        }

        private static Duration positiveDuration(
            Duration value,
            Duration defaultValue,
            String key,
            System.Logger logger
        ) {
            if (value == null || value.isZero() || value.isNegative()) {
                logger.log(System.Logger.Level.WARNING, "Config " + key + " must be > 0; using default " + defaultValue + ".");
                return defaultValue;
            }
            return value;
        }

        private static Duration nonNegativeDuration(
            Duration value,
            Duration defaultValue,
            String key,
            System.Logger logger
        ) {
            if (value == null || value.isNegative()) {
                logger.log(System.Logger.Level.WARNING, "Config " + key + " must be >= 0; using default " + defaultValue + ".");
                return defaultValue;
            }
            return value;
        }

        private static long positiveLong(long value, long defaultValue, String key, System.Logger logger) {
            if (value <= 0L) {
                logger.log(System.Logger.Level.WARNING, "Config " + key + " must be > 0; using default " + defaultValue + ".");
                return defaultValue;
            }
            return value;
        }

        private static long nonNegativeLong(long value, long defaultValue, String key, System.Logger logger) {
            if (value < 0L) {
                logger.log(System.Logger.Level.WARNING, "Config " + key + " must be >= 0; using default " + defaultValue + ".");
                return defaultValue;
            }
            return value;
        }

        private static int nonNegativeInt(int value, int defaultValue, String key, System.Logger logger) {
            if (value < 0) {
                logger.log(System.Logger.Level.WARNING, "Config " + key + " must be >= 0; using default " + defaultValue + ".");
                return defaultValue;
            }
            return value;
        }

        private static String nonBlankString(String value, String defaultValue, String key, System.Logger logger) {
            if (value == null || value.isBlank()) {
                logger.log(
                    System.Logger.Level.WARNING,
                    "Config " + key + " must be non-blank; using default " + defaultValue + "."
                );
                return defaultValue;
            }
            return value.trim();
        }
    }

    private static final class Jfr {
        public Duration maxAge = DEFAULT_JFR_MAX_AGE;
        public long maxSizeBytes = DEFAULT_JFR_MAX_SIZE_BYTES;
        public String recordingName = DEFAULT_JFR_RECORDING_NAME;

        static final BuilderCodec<Jfr> CODEC = BuilderCodec
            .builder(Jfr.class, Jfr::new)
            .addField(new KeyedCodec<>("MaxAge", Codec.DURATION), (c, v) -> {
                if (v != null) {
                    c.maxAge = v;
                }
            }, c -> c.maxAge)
            .addField(new KeyedCodec<>("MaxSizeBytes", Codec.LONG), (c, v) -> {
                if (v != null) {
                    c.maxSizeBytes = v;
                }
            }, c -> c.maxSizeBytes)
            .addField(new KeyedCodec<>("RecordingName", Codec.STRING), (c, v) -> {
                if (v != null) {
                    c.recordingName = v;
                }
            }, c -> c.recordingName)
            .build();
    }

    private static final class Trigger {
        public Duration cooldown = DEFAULT_TRIGGER_COOLDOWN;
        public Duration debounce = DEFAULT_TRIGGER_DEBOUNCE;
        public long stallDegradedMs = DEFAULT_STALL_DEGRADED_MS;
        public long stallCriticalMs = DEFAULT_STALL_CRITICAL_MS;

        static final BuilderCodec<Trigger> CODEC = BuilderCodec
            .builder(Trigger.class, Trigger::new)
            .addField(new KeyedCodec<>("Cooldown", Codec.DURATION), (c, v) -> {
                if (v != null) {
                    c.cooldown = v;
                }
            }, c -> c.cooldown)
            .addField(new KeyedCodec<>("Debounce", Codec.DURATION), (c, v) -> {
                if (v != null) {
                    c.debounce = v;
                }
            }, c -> c.debounce)
            .addField(new KeyedCodec<>("StallDegradedMs", Codec.LONG), (c, v) -> {
                if (v != null) {
                    c.stallDegradedMs = v;
                }
            }, c -> c.stallDegradedMs)
            .addField(new KeyedCodec<>("StallCriticalMs", Codec.LONG), (c, v) -> {
                if (v != null) {
                    c.stallCriticalMs = v;
                }
            }, c -> c.stallCriticalMs)
            .build();
    }

    private static final class Retention {
        public int maxCount = DEFAULT_RETENTION_MAX_COUNT;
        public long maxTotalBytes = DEFAULT_RETENTION_MAX_TOTAL_BYTES;
        public Duration maxAge = DEFAULT_RETENTION_MAX_AGE;

        static final BuilderCodec<Retention> CODEC = BuilderCodec
            .builder(Retention.class, Retention::new)
            .addField(new KeyedCodec<>("MaxCount", Codec.INTEGER), (c, v) -> {
                if (v != null) {
                    c.maxCount = v;
                }
            }, c -> c.maxCount)
            .addField(new KeyedCodec<>("MaxTotalBytes", Codec.LONG), (c, v) -> {
                if (v != null) {
                    c.maxTotalBytes = v;
                }
            }, c -> c.maxTotalBytes)
            .addField(new KeyedCodec<>("MaxAge", Codec.DURATION), (c, v) -> c.maxAge = v, c -> c.maxAge)
            .build();
    }

    private static final class Discord {
        public String webhookUrl = DEFAULT_DISCORD_WEBHOOK_URL;
        public Duration cooldown = DEFAULT_DISCORD_COOLDOWN;
        public Duration requestTimeout = DEFAULT_DISCORD_REQUEST_TIMEOUT;
        public String username = DEFAULT_DISCORD_USERNAME;

        static final BuilderCodec<Discord> CODEC = BuilderCodec
            .builder(Discord.class, Discord::new)
            .addField(new KeyedCodec<>("WebhookUrl", Codec.STRING), (c, v) -> {
                if (v != null) {
                    c.webhookUrl = v;
                }
            }, c -> c.webhookUrl)
            .addField(new KeyedCodec<>("Cooldown", Codec.DURATION), (c, v) -> {
                if (v != null) {
                    c.cooldown = v;
                }
            }, c -> c.cooldown)
            .addField(new KeyedCodec<>("RequestTimeout", Codec.DURATION), (c, v) -> {
                if (v != null) {
                    c.requestTimeout = v;
                }
            }, c -> c.requestTimeout)
            .addField(new KeyedCodec<>("Username", Codec.STRING), (c, v) -> {
                if (v != null) {
                    c.username = v;
                }
            }, c -> c.username)
            .build();
    }

    private static final class Web {
        public boolean enabled = DEFAULT_WEB_ENABLED;

        static final BuilderCodec<Web> CODEC = BuilderCodec
            .builder(Web.class, Web::new)
            .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (c, v) -> {
                if (v != null) {
                    c.enabled = v;
                }
            }, c -> c.enabled)
            .build();
    }
}
