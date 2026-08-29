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
 * Guards the flask render pipeline of {@code design/SPEC.md} §12: the item name key, the base model's
 * "filled" predicate routing to the filled model, and the filled model's two layers (the copper-glass
 * vessel plus the tinted liquid) with both shipped textures. A missing or renamed file here would
 * silently drop the render, so the contract is pinned rather than left to a manual in-game check.
 */
class FlaskResourceContractTest {

    @Test
    void nameAndTooltipKeysAreShipped() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        for (String key : new String[]{
                "item.distillation.flask", "tooltip.distillation.flask.empty",
                "tooltip.distillation.flask.doses", "config.distillation.enableFlask"}) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                    "flask lang key must ship and be non-blank: " + key);
        }
    }

    @Test
    void baseModelRoutesTheFilledPredicate() {
        JsonObject model = shippedJson("/assets/distillation/models/item/flask.json",
                Path.of("src/main/resources/assets/distillation/models/item/flask.json"));
        assertTrue(model.getAsJsonObject("textures").get("layer0").getAsString().equals("distillation:item/flask"),
                "the empty flask model shows the bare vessel");
        assertTrue(model.has("overrides"), "the flask model must carry the filled override");
        boolean routed = model.getAsJsonArray("overrides").asList().stream()
                .map(JsonObject.class::cast)
                .anyMatch(o -> o.getAsJsonObject("predicate").has("distillation:filled")
                        && o.get("model").getAsString().equals("distillation:item/flask_filled"));
        assertTrue(routed, "an override must map the distillation:filled predicate to item/flask_filled");
    }

    @Test
    void filledModelAndTexturesShip() {
        JsonObject filled = shippedJson("/assets/distillation/models/item/flask_filled.json",
                Path.of("src/main/resources/assets/distillation/models/item/flask_filled.json"));
        JsonObject textures = filled.getAsJsonObject("textures");
        assertTrue(textures.get("layer0").getAsString().equals("distillation:item/flask"),
                "the filled model's base layer is the vessel");
        assertTrue(textures.get("layer1").getAsString().equals("distillation:item/flask_liquid"),
                "the filled model's liquid layer is the tinted overlay");
        for (String layer : new String[]{"distillation:item/flask", "distillation:item/flask_liquid"}) {
            String[] parts = layer.split(":", 2);
            assertShippedResource("/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
        }
    }

    @Test
    void theCraftingRecipeShips() {
        // Generated since the datagen conversion, so the source-path fallback names the generated
        // root; the classpath read (build/resources/main, where both main roots merge) is unchanged.
        JsonObject recipe = shippedJson("/data/distillation/recipe/flask.json",
                Path.of("src/main/generated/data/distillation/recipe/flask.json"));
        assertTrue(recipe.getAsJsonObject("result").get("id").getAsString().equals("distillation:flask"),
                "the recipe must produce a flask");
    }

    private static void assertShippedResource(String resourcePath) {
        try (InputStream in = FlaskResourceContractTest.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                return;
            }
        } catch (IOException ignored) {
            // fall through to the file-path check
        }
        // Both main resource roots — a shipped file may be hand-authored or generated, and this
        // guard must not care which.
        assertTrue(Files.exists(Path.of("src/main/resources" + resourcePath))
                        || Files.exists(Path.of("src/main/generated" + resourcePath)),
                "missing shipped resource: " + resourcePath);
    }

    private static JsonObject shippedJson(String resource, Path source) {
        try (InputStream in = FlaskResourceContractTest.class.getResourceAsStream(resource)) {
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
