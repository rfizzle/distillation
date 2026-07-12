// Tier: 2 (fabric-loader-junit + Bootstrap — the BUILTINS table references MobEffects/Items holders)
package com.rfizzle.distillation.brew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the six built-in antidotes against SPEC §6: the effect each cures, its reagent, the index
 * order (the cleanse amplifier), and the lang contract every antidote potion form owes. Registration
 * itself is registry-bound and runs at mod init, so the live brew/cure behavior is a gametest
 * ({@code AntidoteGameTest}); this test pins the declared table and its resources.
 */
class AntidotesTableTest {

    private static final String RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/distillation/lang/en_us.json");

    private record Spec(Holder<MobEffect> target, Item reagent, String path) {
    }

    /** SPEC §6: the eight antidotes, in the order that fixes their cleanse amplifier index. */
    private static final List<Spec> SPEC = List.of(
            new Spec(MobEffects.POISON, Items.FERMENTED_SPIDER_EYE, "poison_antidote"),
            new Spec(MobEffects.WITHER, Items.WITHER_ROSE, "wither_antidote"),
            new Spec(MobEffects.DIG_SLOWDOWN, Items.PRISMARINE_CRYSTALS, "mining_fatigue_antidote"),
            new Spec(MobEffects.BLINDNESS, Items.INK_SAC, "blindness_antidote"),
            new Spec(MobEffects.DARKNESS, Items.ECHO_SHARD, "darkness_antidote"),
            new Spec(MobEffects.LEVITATION, Items.POPPED_CHORUS_FRUIT, "levitation_antidote"),
            new Spec(MobEffects.MOVEMENT_SLOWDOWN, Items.SUGAR, "slowness_antidote"),
            new Spec(MobEffects.WEAKNESS, Items.BLAZE_POWDER, "weakness_antidote"));

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void builtInsMatchTheSpecTableInIndexOrder() {
        assertEquals(SPEC.size(), Antidotes.BUILTINS.size(), "BUILTINS and the spec table cover the same lines");
        for (int i = 0; i < SPEC.size(); i++) {
            Spec spec = SPEC.get(i);
            Antidotes.BuiltIn builtIn = Antidotes.BUILTINS.get(i);
            assertEquals(spec.target(), builtIn.target(), "antidote " + i + " cures the spec effect");
            assertEquals(spec.reagent(), builtIn.reagent(), spec.path() + " brews from its spec reagent");
            assertEquals(spec.path(), builtIn.path(), "antidote " + i + " path");
        }
    }

    @Test
    void everyAntidotePathEndsWithAntidote() {
        for (Antidotes.BuiltIn builtIn : Antidotes.BUILTINS) {
            assertTrue(builtIn.path().endsWith("_antidote"), builtIn.path() + " must read as an antidote");
        }
    }

    @Test
    void everyAntidoteShipsAllFourDisplayNameKeys() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Antidotes.BuiltIn builtIn : Antidotes.BUILTINS) {
            for (String container : new String[]{"potion", "splash_potion", "lingering_potion", "tipped_arrow"}) {
                String key = "item.minecraft." + container + ".effect." + builtIn.path();
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "antidote display-name lang keys missing or blank: " + missing);
    }

    @Test
    void cleanseAndCureTooltipKeysShip() {
        JsonObject lang = lang();
        for (String key : new String[]{"effect.distillation.cleanse", "tooltip.distillation.antidote"}) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key + " must ship a lang value");
        }
    }

    private static JsonObject lang() {
        try (InputStream in = AntidotesTableTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }
}
