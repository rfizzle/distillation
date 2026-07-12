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
 * Guards the antidote render pipeline of {@code design/SPEC.md} §6 and DESIGN §3: the vanilla-potion
 * model override that routes the antidote predicate to the shared antidote model, the model itself,
 * and its greyscale liquid texture (tinted per cure through the vanilla layer system). A missing or
 * renamed file here would silently drop the antidote render, so the contract is pinned.
 */
class AntidoteResourceContractTest {

    @Test
    void potionModelRoutesTheAntidotePredicateToTheAntidoteModel() {
        JsonObject potion = shippedJson("/assets/minecraft/models/item/potion.json",
                Path.of("src/main/resources/assets/minecraft/models/item/potion.json"));
        assertTrue(potion.has("overrides"), "the vanilla potion model must carry the antidote override");
        boolean routed = potion.getAsJsonArray("overrides").asList().stream()
                .map(JsonObject.class::cast)
                .anyMatch(o -> o.getAsJsonObject("predicate").has("distillation:antidote")
                        && o.get("model").getAsString().equals("distillation:item/antidote"));
        assertTrue(routed, "an override must map the distillation:antidote predicate to item/antidote");
    }

    @Test
    void antidoteModelAndTextureShip() {
        JsonObject model = shippedJson("/assets/distillation/models/item/antidote.json",
                Path.of("src/main/resources/assets/distillation/models/item/antidote.json"));
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertTrue(layer0.equals("distillation:item/antidote"),
                "the antidote model's liquid layer must be the antidote texture");
        String[] parts = layer0.split(":", 2);
        assertShippedResource("/assets/" + parts[0] + "/textures/" + parts[1] + ".png");
    }

    private static void assertShippedResource(String resourcePath) {
        try (InputStream in = AntidoteResourceContractTest.class.getResourceAsStream(resourcePath)) {
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
        try (InputStream in = AntidoteResourceContractTest.class.getResourceAsStream(resource)) {
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
