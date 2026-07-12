// Tier: 2 (fabric-loader-junit + Bootstrap — reads vanilla Potions/Items holders)
package com.rfizzle.distillation.brew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the §5 premium line table against SPEC §5.3: the enumerated premium durations, the "own
 * effect reagent" per line, the eligible-line set, and the {@code long ÷ 2} formula (with the three
 * documented exceptions where the enumerated table wins). Plus the "Concentrated" tooltip lang key.
 */
class PremiumBrewsTableTest {

    private static final String RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/distillation/lang/en_us.json");

    /** SPEC §5.3: line path → premium effect duration in ticks. */
    private static final Map<String, Integer> SPEC_TICKS = Map.ofEntries(
            Map.entry("strength", 4800), Map.entry("swiftness", 4800), Map.entry("leaping", 4800),
            Map.entry("regeneration", 1800), Map.entry("poison", 1800), Map.entry("slowness", 2400),
            Map.entry("turtle_master", 2400), Map.entry("resistance", 4800), Map.entry("absorption", 4800),
            Map.entry("haste", 12000));

    /** Lines where the enumerated §5.3 table diverges from the {@code long ÷ 2} guide. */
    private static final Set<String> FORMULA_EXCEPTIONS = Set.of("regeneration", "poison", "turtle_master");

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void premiumDurationsMatchSpecTable() {
        assertEquals(SPEC_TICKS, PremiumBrews.PREMIUM_TICKS, "premium durations must match the SPEC §5.3 table");
    }

    @Test
    void reagentMappingMatchesSpec() {
        Map<String, Item> expected = Map.ofEntries(
                Map.entry("strength", Items.BLAZE_POWDER), Map.entry("swiftness", Items.SUGAR),
                Map.entry("leaping", Items.RABBIT_FOOT), Map.entry("regeneration", Items.GHAST_TEAR),
                Map.entry("poison", Items.SPIDER_EYE), Map.entry("slowness", Items.FERMENTED_SPIDER_EYE),
                Map.entry("turtle_master", Items.TURTLE_HELMET), Map.entry("resistance", Items.SHULKER_SHELL),
                Map.entry("absorption", Items.GOLDEN_APPLE), Map.entry("haste", Items.HONEY_BOTTLE));
        assertEquals(expected, PremiumBrews.REAGENTS, "each line concentrates with its own effect reagent");
    }

    @Test
    void eligibleLinesAreExactlyTheTenStrongFormLines() {
        assertEquals(SPEC_TICKS.keySet(), PremiumBrews.PREMIUM_TICKS.keySet());
        assertEquals(SPEC_TICKS.keySet(), PremiumBrews.REAGENTS.keySet(),
                "the reagent and duration tables cover the same lines");
    }

    @Test
    void premiumFollowsHalfLongExceptForDocumentedLines() {
        Map<String, Holder<Potion>> vanillaLong = Map.of(
                "strength", Potions.LONG_STRENGTH, "swiftness", Potions.LONG_SWIFTNESS,
                "leaping", Potions.LONG_LEAPING, "slowness", Potions.LONG_SLOWNESS);
        for (var entry : PremiumBrews.PREMIUM_TICKS.entrySet()) {
            String path = entry.getKey();
            if (FORMULA_EXCEPTIONS.contains(path)) {
                continue;
            }
            int longTicks = vanillaLong.containsKey(path)
                    ? vanillaLong.get(path).value().getEffects().get(0).getDuration()
                    : distillationLongTicks(path);
            assertEquals(longTicks / 2, entry.getValue().intValue(),
                    path + " premium duration must be its long variant halved");
        }
    }

    @Test
    void concentratedTooltipKeyShips() {
        JsonObject lang = lang();
        String key = "tooltip.distillation.concentrated";
        assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                "the Concentrated tooltip line must ship a lang key");
    }

    /** The §2 lines carry their long duration in {@link DistillationPotions#LINES} (no registration needed). */
    private static int distillationLongTicks(String path) {
        for (DistillationPotions.Line line : DistillationPotions.LINES) {
            if (line.path().equals(path)) {
                return line.longTicks();
            }
        }
        throw new AssertionError("no §2 line for path: " + path);
    }

    private static JsonObject lang() {
        try (InputStream in = PremiumBrewsTableTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }
}
