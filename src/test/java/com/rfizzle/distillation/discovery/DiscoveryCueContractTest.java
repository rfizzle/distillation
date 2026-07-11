// Tier: 1 (pure JUnit — parses shipped JSON off the classpath, no Fabric runtime)
package com.rfizzle.distillation.discovery;

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
 * The shipped companions of the discovery cue ({@code design/SPEC.md} §Sound Design and
 * §Localization): the ✦ toast key, the subtitle key, the {@code sounds.json} wiring, and the
 * rendered Ogg — all of which drift silently (a raw key on screen, a silent chime) with no
 * compile error if any one goes missing.
 */
class DiscoveryCueContractTest {

    private static final String TOAST_KEY = "notification.distillation.recipe_learned";
    private static final String SUBTITLE_KEY = "subtitles.distillation.recipe_learned";
    private static final String SOUND_EVENT = "ui.recipe_learned";

    @Test
    void toastKeyCarriesTheMarkerAndTheOutputSlot() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        assertTrue(lang.has(TOAST_KEY), "missing lang key: " + TOAST_KEY);
        String value = lang.get(TOAST_KEY).getAsString();
        assertTrue(value.contains("✦"), "the discovery marker lives inside the localized value");
        assertTrue(value.contains("%s"), "the toast names the brewed output through an argument");
    }

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
        String oggPath = "/assets/" + parts[0] + "/sounds/" + parts[1] + ".ogg";
        try (InputStream ogg = DiscoveryCueContractTest.class.getResourceAsStream(oggPath)) {
            if (ogg != null) {
                return;
            }
        } catch (IOException ignored) {
            // fall through to the file-path check
        }
        assertTrue(Files.exists(Path.of("src/main/resources" + oggPath)),
                "sounds.json points at a missing ogg: " + oggPath);
    }

    private static JsonObject shippedJson(String resource, Path source) {
        try (InputStream in = DiscoveryCueContractTest.class.getResourceAsStream(resource)) {
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
