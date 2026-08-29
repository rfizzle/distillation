// Tier: 1 (pure JUnit — the shipped advancement JSON and lang file are read from disk as text)
package com.rfizzle.distillation.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Read from the test classpath first — {@code build/resources/main} is the merged product of
     * {@code src/main/resources} and the datagen output in {@code src/main/generated}, and it is
     * what the jar is built from. The source roots below are an IDE-runner fallback only, searched
     * in source-set order; the advancements live in the generated root since the datagen
     * conversion, the lang file in the hand-authored one.
     */
    private static final String CLASSPATH_DIR = "/data/distillation/advancement/";
    private static final List<Path> SOURCE_ROOTS =
            List.of(Path.of("src/main/resources"), Path.of("src/main/generated"));
    private static final String LANG_RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final String PARENT = "minecraft:nether/brew_potion";

    private static final Set<String> IDS = Set.of(
            "trial_and_error", "scholar_of_the_still", "the_missing_shelf", "round_for_the_table",
            "surgical", "the_good_stuff", "every_drop");

    @Test
    void everyAdvancementShipsAndConforms() {
        JsonObject lang = shippedJson(LANG_RESOURCE);
        List<String> problems = new ArrayList<>();
        for (String id : IDS) {
            JsonObject json = shippedJson(CLASSPATH_DIR + id + ".json");
            if (json == null) {
                problems.add(id + ": missing JSON file");
                continue;
            }
            if (!json.has("parent") || !PARENT.equals(json.get("parent").getAsString())) {
                problems.add(id + ": must parent under " + PARENT);
            }
            // Absent is false: `sends_telemetry_event` defaults to false in Advancement's codec, so
            // the datagen providers omit it rather than writing it out. What this guard is for is
            // the flag being turned *on* — which is what Advancement.Builder.advancement() would do
            // if a provider were ever switched to it — so only a present-and-true value fails.
            if (json.has("sends_telemetry_event") && json.get("sends_telemetry_event").getAsBoolean()) {
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
    void theMissingShelfCoversAllSection2Lines() {
        JsonObject json = shippedJson(CLASSPATH_DIR + "the_missing_shelf.json");
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

    /**
     * Loads a shipped resource, classpath first. The fallback walks both {@code main} source roots
     * because the file may be hand-authored or generated, and the guard must not care which.
     */
    private static String shipped(String resource) {
        try (InputStream in = AdvancementResourceContractTest.class.getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new AssertionError("could not read " + resource + " from the test classpath", e);
        }
        for (Path root : SOURCE_ROOTS) {
            Path candidate = root.resolve(resource.substring(1));
            if (Files.exists(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AssertionError("could not read " + candidate, e);
                }
            }
        }
        return null;
    }

    private static JsonObject shippedJson(String resource) {
        String text = shipped(resource);
        return text == null ? null : JsonParser.parseString(text).getAsJsonObject();
    }
}
