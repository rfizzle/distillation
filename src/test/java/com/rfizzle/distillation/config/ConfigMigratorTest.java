package com.rfizzle.distillation.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the raw-JSON migration contract: a pre-versioned (v0) file is stamped to the current
 * version, migration is idempotent, and an already-current file passes through untouched.
 */
class ConfigMigratorTest {

    @Test
    void missingVersionTreatsAsV0AndStampsCurrent() {
        JsonObject json = new JsonObject();
        json.addProperty("enableDiscovery", false);

        boolean changed = ConfigMigrator.migrate(json);

        assertTrue(changed, "a v0 file must be migrated");
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
        assertFalse(json.get("enableDiscovery").getAsBoolean(), "existing keys are carried forward");
    }

    @Test
    void migrationIsIdempotent() {
        JsonObject json = new JsonObject();
        json.addProperty("batchFuelCost", 4);

        assertTrue(ConfigMigrator.migrate(json), "first pass migrates");
        JsonObject afterFirst = json.deepCopy();

        assertFalse(ConfigMigrator.migrate(json), "second pass is a no-op");
        assertEquals(afterFirst, json, "a second pass must not alter the tree");
    }

    @Test
    void currentVersionPassesThroughUntouched() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", ConfigMigrator.CURRENT_VERSION);
        json.addProperty("lingeringCloudRadius", 5.5);
        JsonObject before = json.deepCopy();

        assertFalse(ConfigMigrator.migrate(json), "an already-current file must not be migrated");
        assertEquals(before, json, "an already-current tree must be left untouched");
    }

    @Test
    void nonNumericVersionTreatsAsV0() {
        JsonObject json = new JsonObject();
        json.addProperty("configVersion", "one");

        assertTrue(ConfigMigrator.migrate(json), "a non-numeric version reads as v0");
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
    }
}
