package com.rfizzle.distillation.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.rfizzle.distillation.Distillation;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod's JSON config ({@code config/distillation.json}), per {@code design/SPEC.md}
 * §Configuration. Server-authoritative gameplay keys sit flat at the top level so the JSON keys
 * match the spec's table verbatim; the two render-only client preferences nest under the
 * {@link Client client} block, which is structurally excluded from the server→client sync view
 * ({@link #toSyncJson()}) and never leaves the client.
 */
public class DistillationConfig {
    private static final String CONFIG_FILENAME = "distillation.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    // Serializer for the server→client sync wire form. Compact (no pretty-printing) and drops the
    // client-only {@link #client} block, so the synced view is exactly the server-authoritative
    // gameplay surface. {@link #fromJson(String)} reads this form back.
    private static final Gson SYNC_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return DistillationConfig.class.equals(f.getDeclaringClass()) && "client".equals(f.getName());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    public int configVersion = ConfigMigrator.CURRENT_VERSION;

    public boolean enableDiscovery = true;
    public boolean enableMurkyDraughts = true;
    public boolean startDiscovered = false;
    public boolean enableRecipeNotes = true;
    public boolean enableMissingBrews = true;
    public boolean enableBatchBrewing = true;
    public int batchIngredientCost = 3;
    public int batchFuelCost = 2;
    public boolean enableComparatorOutput = true;
    public boolean enableTippedArrows = true;
    public int tippedArrowsPerDip = 8;
    public boolean enableHonestDurations = true;
    public boolean enableDraughts = true;
    public boolean enableTopUpDrinking = true;
    public boolean enablePremiumBrews = true;
    public boolean enableAntidotes = true;
    public boolean enableThrownRebalance = true;
    public boolean enableAttunedSplash = true;
    public float splashDurationFactor = 0.875f;
    public int lingeringCloudDurationTicks = 1200;
    public float lingeringCloudRadius = 4.5f;

    public Client client = new Client();

    /** Render-only preferences; never synced, read only on the client. */
    public static class Client {
        public boolean showVaporHints = true;
        public boolean recipeViewerShowsUndiscovered = false;
        public boolean smoothNightVisionFade = true;
    }

    public static DistillationConfig load() {
        return load(configPath());
    }

    static DistillationConfig load(Path path) {
        if (!Files.exists(path)) {
            Distillation.LOGGER.info("Config file missing; creating default at {}", path);
            DistillationConfig config = new DistillationConfig();
            config.save(path);
            return config;
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path));
            if (element == null || !element.isJsonObject()) {
                // Same contract as the unparseable branch below: the user's file — however
                // malformed — is theirs to fix; run on defaults and leave it untouched.
                Distillation.LOGGER.warn(
                        "Config file at {} was empty or not a JSON object; using defaults (existing file left untouched)", path);
                DistillationConfig fallback = new DistillationConfig();
                fallback.fillDefaults();
                fallback.clamp();
                return fallback;
            }
            // Migrate the raw JSON tree before deserialize so a renamed key survives (a lenient
            // Gson deserialize would drop it). A file without configVersion is treated as v0.
            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);
            DistillationConfig config = GSON.fromJson(raw, DistillationConfig.class);
            config.fillDefaults();
            config.clamp();
            if (migrated) {
                config.save(path);
            }
            return config;
        } catch (JsonSyntaxException e) {
            Distillation.LOGGER.error("Failed to parse config at {}; using defaults (existing file left untouched)", path, e);
            DistillationConfig fallback = new DistillationConfig();
            fallback.fillDefaults();
            fallback.clamp();
            return fallback;
        } catch (IOException e) {
            Distillation.LOGGER.error("Failed to read config at {}; using defaults", path, e);
            DistillationConfig fallback = new DistillationConfig();
            fallback.fillDefaults();
            fallback.clamp();
            return fallback;
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        // Write to a sibling temp file then atomically rename, so a crash or kill mid-write can
        // never leave a truncated/corrupt config in place. Fall back to a plain move where the
        // filesystem can't do an atomic rename, and clean up the orphan temp on failure.
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Distillation.LOGGER.error("Failed to save config to {}", path, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Distillation.LOGGER.warn("Failed to clean up orphan temp config {}", tmp, cleanup);
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME);
    }

    /**
     * Serializes the server-authoritative gameplay surface for the config-sync payload: every key
     * except the client-only {@link #client} block. Read back with {@link #fromJson(String)}.
     */
    public String toSyncJson() {
        return SYNC_GSON.toJson(this);
    }

    /**
     * Reconstructs a config from a {@link #toSyncJson()} string received from the server. The JSON
     * is already at the current schema version (both sides run the same mod build), so migration is
     * deliberately skipped — only {@link #fillDefaults()} null-healing and {@link #clamp()}
     * warn-and-clamp run, so a hostile or malformed payload can never yield an out-of-range config.
     * A null/unparseable tree falls back to a fresh default rather than throwing.
     */
    public static DistillationConfig fromJson(String json) {
        DistillationConfig config;
        try {
            config = GSON.fromJson(json, DistillationConfig.class);
        } catch (JsonParseException e) {
            Distillation.LOGGER.warn("Failed to parse synced config JSON; using defaults", e);
            config = null;
        }
        if (config == null) {
            config = new DistillationConfig();
        }
        config.fillDefaults();
        config.clamp();
        return config;
    }

    /**
     * A deep, independent copy — the ModMenu screen edits one of these so the live config is
     * never mutated in place; the clamped copy is published back with a single reference swap
     * ({@link com.rfizzle.distillation.Distillation#updateConfig}).
     */
    public DistillationConfig copy() {
        return GSON.fromJson(GSON.toJson(this), DistillationConfig.class);
    }

    private void fillDefaults() {
        if (client == null) client = new Client();
    }

    /**
     * Warn-and-clamp every ranged field, logging each correction. Ranges come from the per-feature
     * Config subsections of {@code design/SPEC.md} (§3 batch costs, §7 thrown rebalance), mirrored
     * on {@code site/pages/config.json}. Public so the ModMenu screen can clamp before
     * {@link #save()} — an out-of-range value typed into the config GUI is corrected rather than
     * persisted verbatim.
     */
    public void clamp() {
        batchIngredientCost = clampIntRange("batchIngredientCost", batchIngredientCost, 2, 6);
        batchFuelCost = clampIntRange("batchFuelCost", batchFuelCost, 1, 4);
        tippedArrowsPerDip = clampIntRange("tippedArrowsPerDip", tippedArrowsPerDip, 1, 16);
        splashDurationFactor = clampFloatRange("splashDurationFactor", splashDurationFactor, 0.5f, 1.0f);
        lingeringCloudDurationTicks = clampIntRange("lingeringCloudDurationTicks", lingeringCloudDurationTicks, 600, 2400);
        lingeringCloudRadius = clampFloatRange("lingeringCloudRadius", lingeringCloudRadius, 3.0f, 6.0f);
    }

    private static int clampIntRange(String name, int value, int min, int max) {
        if (value < min) {
            Distillation.LOGGER.warn("clamped {} from {} to {}", name, value, min);
            return min;
        }
        if (value > max) {
            Distillation.LOGGER.warn("clamped {} from {} to {}", name, value, max);
            return max;
        }
        return value;
    }

    private static float clampFloatRange(String name, float value, float min, float max) {
        if (!(value >= min)) { // also catches NaN
            Distillation.LOGGER.warn("clamped {} from {} to {}", name, value, min);
            return min;
        }
        if (value > max) {
            Distillation.LOGGER.warn("clamped {} from {} to {}", name, value, max);
            return max;
        }
        return value;
    }
}
