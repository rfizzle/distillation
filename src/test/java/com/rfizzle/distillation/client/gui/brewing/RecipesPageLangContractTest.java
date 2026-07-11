package com.rfizzle.distillation.client.gui.brewing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the recipes-page lang contract: every {@code gui.distillation.recipes_page.*} key the
 * renderer names as a constant has a non-blank {@code en_us.json} entry, and every such lang key
 * maps back to a constant. A key used without its translation (or a stale translation) fails here
 * instead of rendering raw in the overlay.
 */
class RecipesPageLangContractTest {

    private static final String RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/distillation/lang/en_us.json");
    private static final String PREFIX = "gui.distillation.recipes_page.";

    private static JsonObject lang() {
        try (InputStream in = RecipesPageLangContractTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    private static List<String> constantKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : RecipesPageRenderer.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            try {
                String value = (String) field.get(null);
                if (value != null && value.startsWith(PREFIX)) {
                    keys.add(value);
                }
            } catch (IllegalAccessException ignored) {
                // non-public constant; skip
            }
        }
        return keys;
    }

    @Test
    void everyReferencedKeyHasANonBlankEntry() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : constantKeys()) {
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "recipes-page lang keys missing or blank in en_us.json: " + missing);
    }

    @Test
    void everyLangEntryMapsBackToAConstant() {
        JsonObject lang = lang();
        List<String> known = constantKeys();
        List<String> orphaned = new ArrayList<>();
        for (String langKey : lang.keySet()) {
            if (langKey.startsWith(PREFIX) && !known.contains(langKey)) {
                orphaned.add(langKey);
            }
        }
        assertTrue(orphaned.isEmpty(),
                "recipes-page lang keys with no matching RecipesPageRenderer constant (renamed?): " + orphaned);
    }
}
