// Tier: 1 (pure JUnit — parses shipped JSON off the classpath, no Fabric runtime)
package com.rfizzle.distillation.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped companions of the murky fizzle ({@code design/SPEC.md} §Sound Design and
 * §Localization): the subtitle key, the {@code sounds.json} wiring, and the rendered Ogg — all of
 * which drift silently (a raw key on screen, a silent stand) with no compile error if one goes
 * missing.
 */
class MurkyCueContractTest {

    private static final String SUBTITLE_KEY = "subtitles.distillation.murky";
    private static final String SOUND_EVENT = "block.brewing_stand.murky";

    @Test
    void subtitleKeyExists() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        assertTrue(lang.has(SUBTITLE_KEY), "missing lang key: " + SUBTITLE_KEY);
        assertFalse(lang.get(SUBTITLE_KEY).getAsString().isBlank(), "subtitle must not be blank");
    }

    @Test
    void soundsJsonWiresTheEventSubtitleAndShippedOgg() {
        JsonObject sounds = shippedJson("/assets/distillation/sounds.json",
                Path.of("src/main/resources/assets/distillation/sounds.json"));
        assertTrue(sounds.has(SOUND_EVENT), "sounds.json is missing " + SOUND_EVENT);

        JsonObject event = sounds.getAsJsonObject(SOUND_EVENT);
        assertEquals(SUBTITLE_KEY, event.get("subtitle").getAsString(),
                "the event's subtitle must be the SPEC's key");

        var entries = event.getAsJsonArray("sounds");
        assertEquals(1, entries.size(), "one rendered cue per event");
        String sound = entries.get(0).getAsString();
        String[] parts = sound.split(":", 2);
        assertShippedResource("/assets/" + parts[0] + "/sounds/" + parts[1] + ".ogg");
    }

    @Test
    void itemAndTooltipLangKeysExist() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        assertTrue(lang.has("item.distillation.murky_draught"), "missing item name key");
        assertTrue(lang.has("tooltip.distillation.murky.hint"), "missing hint tooltip key");
        assertTrue(lang.get("tooltip.distillation.murky.hint").getAsString().contains("%s"),
                "the hint line names the ingredient through an argument");
        assertTrue(lang.has("tooltip.distillation.murky.hintless"), "missing hintless tooltip key");
        assertFalse(lang.get("tooltip.distillation.murky.hintless").getAsString().contains("%s"),
                "the hintless line takes no argument");
    }

    @Test
    void itemModelParentsGeneratedAndItsTextureShips() {
        JsonObject model = shippedJson("/assets/distillation/models/item/murky_draught.json",
                Path.of("src/main/resources/assets/distillation/models/item/murky_draught.json"));
        assertEquals("minecraft:item/generated", model.get("parent").getAsString(),
                "a flat 2D item sprite parents item/generated");
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        String[] parts = layer0.split(":", 2);
        assertShippedResource("/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
    }

    static void assertShippedResource(String resourcePath) {
        try (InputStream in = MurkyCueContractTest.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                return;
            }
        } catch (IOException ignored) {
            // fall through to the file-path check
        }
        assertTrue(Files.exists(Path.of("src/main/resources" + resourcePath)),
                "missing shipped resource: " + resourcePath);
    }

    static JsonObject shippedJson(String resource, Path source) {
        try (InputStream in = MurkyCueContractTest.class.getResourceAsStream(resource)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(source, StandardCharsets.UTF_8);
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            assertNotNull(parsed);
            return parsed;
        } catch (IOException e) {
            throw new AssertionError("could not load " + resource, e);
        }
    }
}
