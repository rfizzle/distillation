// Tier: 1 (pure JUnit — the shipped advancement JSON and lang file are read from disk as text)
package com.rfizzle.distillation.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the hand-written §11 advancement tree against SPEC: the seven ids ship, each parents under
 * vanilla's Local Brewery, keeps telemetry off, fires only {@code distillation:} triggers, and has
 * a non-blank title and description lang key. The Missing Shelf must carry exactly every §2 line.
 * The live-registry parse (that each trigger id resolves) is the gametest's job; this guards the
 * static resources without a running server.
 */
class AdvancementResourceContractTest {

    private static final Path DIR = Path.of("src/main/resources/data/distillation/advancement");
    private static final Path LANG = Path.of("src/main/resources/assets/distillation/lang/en_us.json");
    private static final String PARENT = "minecraft:nether/brew_potion";

    private static final Set<String> IDS = Set.of(
            "trial_and_error", "scholar_of_the_still", "the_missing_shelf", "round_for_the_table",
            "surgical", "the_good_stuff", "every_drop");

    @Test
    void everyAdvancementShipsAndConforms() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> problems = new ArrayList<>();
        for (String id : IDS) {
            Path file = DIR.resolve(id + ".json");
            if (!Files.exists(file)) {
                problems.add(id + ": missing JSON file");
                continue;
            }
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("parent") || !PARENT.equals(json.get("parent").getAsString())) {
                problems.add(id + ": must parent under " + PARENT);
            }
            if (!json.has("sends_telemetry_event") || json.get("sends_telemetry_event").getAsBoolean()) {
                problems.add(id + ": sends_telemetry_event must be false");
            }
            JsonObject display = json.getAsJsonObject("display");
            for (String field : new String[]{"title", "description"}) {
                String key = display.getAsJsonObject(field).get("translate").getAsString();
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    problems.add(id + ": lang key missing or blank: " + key);
                }
            }
            JsonObject criteria = json.getAsJsonObject("criteria");
            if (criteria.entrySet().isEmpty()) {
                problems.add(id + ": no criteria");
            }
            for (var entry : criteria.entrySet()) {
                String trigger = entry.getValue().getAsJsonObject().get("trigger").getAsString();
                if (!trigger.startsWith("distillation:")) {
                    problems.add(id + ": criterion " + entry.getKey() + " fires a non-Distillation trigger " + trigger);
                }
            }
        }
        assertTrue(problems.isEmpty(), "advancement contract violations: " + problems);
    }

    @Test
    void theMissingShelfCoversAllSection2Lines() throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(DIR.resolve("the_missing_shelf.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject criteria = json.getAsJsonObject("criteria");
        Set<String> lines = new TreeSet<>();
        for (var entry : criteria.entrySet()) {
            JsonObject conditions = entry.getValue().getAsJsonObject().getAsJsonObject("conditions");
            assertEquals("distillation:missing_line_brewed",
                    entry.getValue().getAsJsonObject().get("trigger").getAsString(),
                    "every Missing Shelf criterion fires the missing-line trigger");
            lines.add(conditions.get("line").getAsString());
        }
        assertEquals(
                Set.of("resistance", "haste", "absorption", "luck", "glowing", "levitation", "health_boost"),
                lines, "The Missing Shelf pins exactly the §2 lines");

        JsonArray requirements = json.getAsJsonArray("requirements");
        assertEquals(7, requirements.size(), "every criterion is required (AND), each in its own group");
        for (var group : requirements) {
            assertFalse(group.getAsJsonArray().isEmpty(), "each requirement group names its criterion");
        }
    }
}
