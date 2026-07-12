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
 * Guards the half-draught render pipeline of {@code design/SPEC.md} §4: the "(Half)" name key, the
 * vanilla-potion model override that routes the draught marker to the half model, the half model
 * itself, and the derived liquid texture. A missing or renamed file here would silently drop the
 * half-empty render, so the contract is pinned rather than left to a manual in-game check.
 */
class DraughtResourceContractTest {

    @Test
    void halfNameKeyIsShipped() {
        JsonObject lang = shippedJson("/assets/distillation/lang/en_us.json",
                Path.of("src/main/resources/assets/distillation/lang/en_us.json"));
        assertTrue(lang.has("item.distillation.draught.half")
                        && lang.get("item.distillation.draught.half").getAsString().contains("%s"),
                "the (Half) name key must ship and wrap the base name");
    }

    @Test
    void potionModelRoutesTheDraughtMarkerToTheHalfModel() {
        JsonObject potion = shippedJson("/assets/minecraft/models/item/potion.json",
                Path.of("src/main/resources/assets/minecraft/models/item/potion.json"));
        assertTrue(potion.has("overrides"), "the vanilla potion model must carry the draught override");
        boolean routed = potion.getAsJsonArray("overrides").asList().stream()
                .map(JsonObject.class::cast)
                .anyMatch(o -> o.getAsJsonObject("predicate").has("distillation:draught")
                        && o.get("model").getAsString().equals("distillation:item/potion_half"));
        assertTrue(routed, "an override must map the distillation:draught predicate to item/potion_half");
    }

    @Test
    void halfModelAndTextureShip() {
        JsonObject half = shippedJson("/assets/distillation/models/item/potion_half.json",
                Path.of("src/main/resources/assets/distillation/models/item/potion_half.json"));
        String layer0 = half.getAsJsonObject("textures").get("layer0").getAsString();
        assertTrue(layer0.equals("distillation:item/draught_half"),
                "the half model's liquid layer must be the draught texture");
        String[] parts = layer0.split(":", 2);
        assertShippedResource("/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
    }

    private static void assertShippedResource(String resourcePath) {
        try (InputStream in = DraughtResourceContractTest.class.getResourceAsStream(resourcePath)) {
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
        try (InputStream in = DraughtResourceContractTest.class.getResourceAsStream(resource)) {
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
