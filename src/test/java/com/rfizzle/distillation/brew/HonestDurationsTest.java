// Tier: 2 (fabric-loader-junit + Bootstrap — the exemption check reads the potion registry via LINES)
package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.Distillation;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link HonestDurations#OVERRIDES} table against the SPEC §4 durations, confirms every
 * overridden id is a real registered vanilla potion, and guards the exemption acceptance criterion:
 * Distillation's own §2 lines never appear in the override table, so they can never double-scale.
 */
class HonestDurationsTest {

    /** SPEC §4 table: vanilla utility potion path → retuned ticks. */
    private static final Map<String, Integer> SPEC = specTable();

    private static Map<String, Integer> specTable() {
        Map<String, Integer> spec = new LinkedHashMap<>();
        spec.put("fire_resistance", 9600);
        spec.put("long_fire_resistance", 24000);
        spec.put("water_breathing", 9600);
        spec.put("long_water_breathing", 24000);
        spec.put("night_vision", 9600);
        spec.put("long_night_vision", 24000);
        spec.put("invisibility", 9600);
        spec.put("long_invisibility", 24000);
        spec.put("slow_falling", 4800);
        spec.put("long_slow_falling", 12000);
        return spec;
    }

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void overridesMatchTheSpecTable() {
        assertEquals(SPEC.size(), HonestDurations.OVERRIDES.size(),
                "OVERRIDES and the SPEC §4 table cover the same lines");
        SPEC.forEach((path, ticks) -> assertEquals(ticks,
                HonestDurations.OVERRIDES.get(ResourceLocation.withDefaultNamespace(path)),
                path + " retuned duration"));
    }

    @Test
    void everyOverriddenPotionIsARegisteredVanillaPotion() {
        HonestDurations.OVERRIDES.keySet().forEach(id -> assertTrue(
                BuiltInRegistries.POTION.containsKey(id), "no registered potion for override id " + id));
    }

    @Test
    void distillationOwnLinesAreExemptFromTheTable() {
        for (var line : com.rfizzle.distillation.brew.DistillationPotions.LINES) {
            for (String path : new String[]{line.path(), "long_" + line.path(), "strong_" + line.path()}) {
                assertTrue(!HonestDurations.OVERRIDES.containsKey(Distillation.id(path)),
                        "a Distillation line must never be in the override table: " + path);
            }
        }
    }

    @Test
    void halfFloorsTheDuration() {
        assertEquals(4800, HonestDurations.half(9600));
        assertEquals(2400, HonestDurations.half(4801));
        assertEquals(0, HonestDurations.half(0));
    }
}
