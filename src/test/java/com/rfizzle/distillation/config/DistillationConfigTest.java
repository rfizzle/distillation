package com.rfizzle.distillation.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the load/save lifecycle of {@link DistillationConfig} against a temp directory:
 * first-launch default write, round-trip, warn-and-clamp bounds, lenient partial files, the
 * corrupted-file fallback (file left untouched), and the sync view's client-block exclusion.
 */
class DistillationConfigTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("distillation.json");
    }

    @Test
    void firstLaunchWritesEverySpecDefault() throws IOException {
        Path path = configFile();
        DistillationConfig loaded = DistillationConfig.load(path);

        assertTrue(Files.exists(path), "first launch must create the config file");
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

        assertEquals(1, json.get("configVersion").getAsInt());
        assertTrue(json.get("enableDiscovery").getAsBoolean());
        assertTrue(json.get("enableMurkyDraughts").getAsBoolean());
        assertFalse(json.get("startDiscovered").getAsBoolean());
        assertTrue(json.get("enableMissingBrews").getAsBoolean());
        assertTrue(json.get("enableBatchBrewing").getAsBoolean());
        assertEquals(3, json.get("batchIngredientCost").getAsInt());
        assertEquals(2, json.get("batchFuelCost").getAsInt());
        assertTrue(json.get("enableHonestDurations").getAsBoolean());
        assertTrue(json.get("enableDraughts").getAsBoolean());
        assertTrue(json.get("enablePremiumBrews").getAsBoolean());
        assertTrue(json.get("enableAntidotes").getAsBoolean());
        assertTrue(json.get("enableThrownRebalance").getAsBoolean());
        assertEquals(0.875f, json.get("splashDurationFactor").getAsFloat());
        assertEquals(1200, json.get("lingeringCloudDurationTicks").getAsInt());
        assertEquals(4.5f, json.get("lingeringCloudRadius").getAsFloat());

        JsonObject client = json.getAsJsonObject("client");
        assertTrue(client.get("showVaporHints").getAsBoolean());
        assertFalse(client.get("recipeViewerShowsUndiscovered").getAsBoolean());

        assertEquals(1, loaded.configVersion);
    }

    @Test
    void savedValuesRoundTrip() {
        Path path = configFile();
        DistillationConfig config = new DistillationConfig();
        config.enableDiscovery = false;
        config.startDiscovered = true;
        config.batchIngredientCost = 5;
        config.splashDurationFactor = 0.75f;
        config.lingeringCloudDurationTicks = 900;
        config.client.showVaporHints = false;
        config.save(path);

        DistillationConfig loaded = DistillationConfig.load(path);
        assertFalse(loaded.enableDiscovery);
        assertTrue(loaded.startDiscovered);
        assertEquals(5, loaded.batchIngredientCost);
        assertEquals(0.75f, loaded.splashDurationFactor);
        assertEquals(900, loaded.lingeringCloudDurationTicks);
        assertFalse(loaded.client.showVaporHints);
    }

    @Test
    void outOfRangeValuesClampOnLoad() throws IOException {
        Path path = configFile();
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "batchIngredientCost": 99,
                  "batchFuelCost": 0,
                  "splashDurationFactor": 0.1,
                  "lingeringCloudDurationTicks": 99999,
                  "lingeringCloudRadius": -1.0
                }
                """);

        DistillationConfig loaded = DistillationConfig.load(path);
        assertEquals(6, loaded.batchIngredientCost, "batchIngredientCost clamps to its 2–6 range");
        assertEquals(1, loaded.batchFuelCost, "batchFuelCost clamps to its 1–4 range");
        assertEquals(0.5f, loaded.splashDurationFactor, "splashDurationFactor clamps to its 0.5–1.0 range");
        assertEquals(2400, loaded.lingeringCloudDurationTicks, "lingeringCloudDurationTicks clamps to its 600–2400 range");
        assertEquals(3.0f, loaded.lingeringCloudRadius, "lingeringCloudRadius clamps to its 3.0–6.0 range");
    }

    @Test
    void unknownFieldsIgnoredAndMissingFieldsDefaultFilled() throws IOException {
        Path path = configFile();
        // A partial hand-edited file: one known key, one unknown key, no client block.
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "enableAntidotes": false,
                  "someKeyFromTheFuture": 42
                }
                """);

        DistillationConfig loaded = DistillationConfig.load(path);
        assertFalse(loaded.enableAntidotes, "the present key is honored");
        assertTrue(loaded.enableDiscovery, "missing keys fill with defaults");
        assertEquals(3, loaded.batchIngredientCost, "missing keys fill with defaults");
        assertTrue(loaded.client.showVaporHints, "a missing client block null-heals to defaults");
    }

    @Test
    void corruptedFileFallsBackToDefaultsAndIsLeftUntouched() throws IOException {
        Path path = configFile();
        String garbage = "{ this is not json";
        Files.writeString(path, garbage);

        DistillationConfig loaded = DistillationConfig.load(path);
        assertTrue(loaded.enableDiscovery, "fallback runs on defaults");
        assertEquals(3, loaded.batchIngredientCost, "fallback runs on defaults");
        assertEquals(garbage, Files.readString(path),
                "an unparseable file must be left byte-identical for the user to fix");
    }

    @Test
    void missingConfigVersionMigratesAndPersistsTheStamp() throws IOException {
        Path path = configFile();
        // A pre-versioned file: valid JSON object, no configVersion. Migration stamps it to the
        // current version and persists the upgraded schema back to disk.
        Files.writeString(path, """
                {
                  "enableDraughts": false
                }
                """);

        DistillationConfig loaded = DistillationConfig.load(path);
        assertFalse(loaded.enableDraughts, "existing values carry through migration");
        assertEquals(1, loaded.configVersion);

        JsonObject reread = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, reread.get("configVersion").getAsInt(),
                "the migrated schema version is persisted back to disk");
        assertFalse(reread.get("enableDraughts").getAsBoolean(),
                "the user's setting survives the migration re-save");
    }

    @Test
    void syncJsonOmitsTheClientBlock() {
        DistillationConfig config = new DistillationConfig();
        config.client.showVaporHints = false;

        String sync = config.toSyncJson();
        JsonObject json = JsonParser.parseString(sync).getAsJsonObject();
        assertFalse(json.has("client"), "the client block never leaves the server");
        assertTrue(json.has("enableDiscovery"), "server keys are all present in the sync view");
        assertTrue(json.has("lingeringCloudRadius"), "server keys are all present in the sync view");
    }

    @Test
    void copyIsDeepAndIndependentOfTheOriginal() {
        DistillationConfig original = new DistillationConfig();
        original.enableAntidotes = false;
        original.batchFuelCost = 4;
        original.client.showVaporHints = false;

        DistillationConfig copy = original.copy();
        assertFalse(copy.enableAntidotes, "the copy carries the original's values");
        assertEquals(4, copy.batchFuelCost, "the copy carries the original's values");
        assertFalse(copy.client.showVaporHints, "the nested client block is copied too");

        // Mutating the copy (as the ModMenu screen does) must never touch the live original.
        copy.enableAntidotes = true;
        copy.batchFuelCost = 1;
        copy.client.showVaporHints = true;
        assertFalse(original.enableAntidotes, "the original is isolated from copy edits");
        assertEquals(4, original.batchFuelCost, "the original is isolated from copy edits");
        assertFalse(original.client.showVaporHints, "the nested client block is deep-copied, not shared");
    }

    @Test
    void fromJsonClampsHostileValuesAndDefaultsOnGarbage() {
        DistillationConfig hostile = DistillationConfig.fromJson(
                "{\"batchIngredientCost\": 9999, \"splashDurationFactor\": -5.0}");
        assertEquals(6, hostile.batchIngredientCost, "a hostile payload can't produce out-of-range values");
        assertEquals(0.5f, hostile.splashDurationFactor, "a hostile payload can't produce out-of-range values");

        DistillationConfig garbage = DistillationConfig.fromJson("not json at all");
        assertEquals(3, garbage.batchIngredientCost, "unparseable sync JSON falls back to defaults");

        DistillationConfig nullJson = DistillationConfig.fromJson("null");
        assertEquals(3, nullJson.batchIngredientCost, "a null tree falls back to defaults");
    }
}
