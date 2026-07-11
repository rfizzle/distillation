package com.rfizzle.distillation.config;

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
 * Tier 1 — the config lang contract: every {@link DistillationConfig} key (server and client) has a
 * {@code config.distillation.<snake_case>} label and a non-blank {@code .tooltip}, plus the screen
 * title and both category headers, so the Cloth screen never renders a raw translation key. The key
 * roster is derived from the POJO by reflection, so a field added without its lang pair fails here
 * instead of in-game.
 */
class ConfigLangContractTest {

    private static final String RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/distillation/lang/en_us.json");
    private static final String PREFIX = "config.distillation.";

    private static JsonObject lang() {
        try (InputStream in = ConfigLangContractTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    private static List<String> configKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : DistillationConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String name = field.getName();
            if (name.equals("configVersion") || name.equals("client")) continue;
            keys.add(toSnakeCase(name));
        }
        for (Field field : DistillationConfig.Client.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            keys.add(toSnakeCase(field.getName()));
        }
        return keys;
    }

    private static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    @Test
    void everyConfigKeyHasLabelAndTooltip() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        List<String> blank = new ArrayList<>();
        for (String key : configKeys()) {
            for (String langKey : new String[]{PREFIX + key, PREFIX + key + ".tooltip"}) {
                if (!lang.has(langKey)) {
                    missing.add(langKey);
                } else if (lang.get(langKey).getAsString().isBlank()) {
                    blank.add(langKey);
                }
            }
        }
        assertTrue(missing.isEmpty(), "config lang keys missing from en_us.json: " + missing);
        assertTrue(blank.isEmpty(), "config lang keys blank in en_us.json: " + blank);
    }

    @Test
    void titleAndCategoryKeysExist() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : new String[]{
                PREFIX + "title", PREFIX + "category.server", PREFIX + "category.client"}) {
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "screen chrome lang keys missing or blank: " + missing);
    }

    @Test
    void everyConfigLangEntryMapsBackToARealField() {
        JsonObject lang = lang();
        List<String> knownKeys = configKeys();
        List<String> orphaned = new ArrayList<>();
        for (String langKey : lang.keySet()) {
            if (!langKey.startsWith(PREFIX)) continue;
            String remainder = langKey.substring(PREFIX.length());
            if (remainder.equals("title") || remainder.startsWith("category.")) continue;
            String base = remainder.endsWith(".tooltip")
                    ? remainder.substring(0, remainder.length() - ".tooltip".length())
                    : remainder;
            if (!knownKeys.contains(base)) {
                orphaned.add(langKey);
            }
        }
        assertTrue(orphaned.isEmpty(),
                "config lang keys with no matching DistillationConfig field (renamed or removed?): " + orphaned);
    }
}
