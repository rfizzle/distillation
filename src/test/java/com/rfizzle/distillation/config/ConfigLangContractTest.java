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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the config lang contract: every {@link DistillationConfig} field (server and client) has a
 * {@code config.distillation.<fieldName>} label and a non-blank {@code .tooltip}, plus the screen
 * title and both category headers, so the Cloth screen never renders a raw translation key. The key
 * roster is derived from the POJO by reflection, so a field added without its lang pair fails here
 * instead of in-game.
 *
 * <p>The label key <em>is</em> the Java field name, verbatim. DESIGN-SYSTEM §10's casing table rules
 * casing by surface rather than globally, and puts {@code config.<mod>.<field>} labels and their
 * {@code .tooltip} pairs in camelCase on exactly this basis: "the key mirrors the Java config field
 * it labels, so field and key stay mechanically aligned". Deriving the key by identity rather than
 * by a snake_case transform is what makes that alignment mechanical instead of a convention someone
 * has to remember — a renamed field now fails here by construction.
 *
 * <p>{@code config.<mod>.category.<name>} stays snake_case (a category names a section, not a
 * field), which is why the categories are asserted separately below.
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
            keys.add(name);
        }
        for (Field field : DistillationConfig.Client.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            keys.add(field.getName());
        }
        return keys;
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

    /**
     * The §10 casing rule, stated directly rather than left implicit in the identity mapping above.
     * A field label reintroduced in snake_case would still fail {@code everyConfigKeyHasLabelAndTooltip}
     * — but as "missing key", which points at the wrong problem. This names it.
     */
    @Test
    void fieldLabelsAreCamelCaseAndCategoriesAreSnakeCase() {
        JsonObject lang = lang();
        List<String> wrongCase = new ArrayList<>();
        for (String langKey : lang.keySet()) {
            if (!langKey.startsWith(PREFIX)) continue;
            String remainder = langKey.substring(PREFIX.length());
            if (remainder.equals("title")) continue;
            if (remainder.startsWith("category.")) {
                // A category names a section, not a field — snake_case, and never camelCase.
                if (!remainder.equals(remainder.toLowerCase(Locale.ROOT))) {
                    wrongCase.add(langKey + " (category keys stay snake_case)");
                }
                continue;
            }
            String base = remainder.endsWith(".tooltip")
                    ? remainder.substring(0, remainder.length() - ".tooltip".length())
                    : remainder;
            if (base.indexOf('_') >= 0) {
                wrongCase.add(langKey + " (field labels are camelCase, mirroring the Java field)");
            }
        }
        assertTrue(wrongCase.isEmpty(),
                "config lang keys violating DESIGN-SYSTEM §10 casing: " + wrongCase);
    }
}
