// Tier: 1 (pure JUnit — parses shipped JSON/PNG off the classpath, no Fabric runtime)
package com.rfizzle.distillation.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the recipe-note render pipeline of {@code design/SPEC.md} §1: the item name key, the two
 * tooltip lines (recipe + "brew it" reminder) and the unreadable fallback, the generated item model,
 * and its liquid-free parchment texture. A missing or renamed file here would ship a note that reads
 * as a magenta-and-black error or an untranslated key, so the contract is pinned rather than left to
 * a manual in-game check.
 */
class RecipeNoteResourceContractTest {

    @Test
    void nameAndTooltipKeysAreShipped() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        assertTrue(lang.has("item.distillation.recipe_note"), "the note item name key must ship");
        assertTrue(lang.has("tooltip.distillation.recipe_note.recipe")
                        && lang.get("tooltip.distillation.recipe_note.recipe").getAsString().contains("%1$s")
                        && lang.get("tooltip.distillation.recipe_note.recipe").getAsString().contains("%3$s"),
                "the recipe line must ship and reference its input and output args");
        assertTrue(lang.has("tooltip.distillation.recipe_note.hint"),
                "the brew-it reminder line must ship");
        assertTrue(lang.has("tooltip.distillation.recipe_note.unknown"),
                "the unreadable-recipe fallback line must ship");
    }

    @Test
    void modelRoutesToTheParchmentTexture() {
        JsonObject model = shippedJson("/assets/distillation/models/item/recipe_note.json",
                Path.of("src/main/resources/assets/distillation/models/item/recipe_note.json"));
        assertTrue(model.get("parent").getAsString().equals("minecraft:item/generated"),
                "the note uses the flat generated item model");
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertTrue(layer0.equals("distillation:item/recipe_note"),
                "the note model's layer0 must be the note texture");
        String[] parts = layer0.split(":", 2);
        assertShippedResource("/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
    }

    private static void assertShippedResource(String resourcePath) {
        try (InputStream in = RecipeNoteResourceContractTest.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                return;
            }
        } catch (IOException ignored) {
            // fall through to the file-path check
        }
        assertTrue(Files.exists(Path.of("src/main/resources" + resourcePath)),
                "missing shipped resource: " + resourcePath);
    }

    private static JsonObject shippedJson(String resource, Path source) {
        try (InputStream in = RecipeNoteResourceContractTest.class.getResourceAsStream(resource)) {
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
