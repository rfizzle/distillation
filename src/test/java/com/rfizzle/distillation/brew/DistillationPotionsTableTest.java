// Tier: 2 (fabric-loader-junit + Bootstrap — the LINES table references MobEffects holders)
package com.rfizzle.distillation.brew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link DistillationPotions#LINES} declaration against the SPEC §2 table (durations,
 * level-II availability, effect wiring) and the lang contract it implies: every line whose display
 * name vanilla does not already localize ships all four {@code item.minecraft.*.effect.<name>}
 * keys, so no potion, splash, lingering, or tipped-arrow form ever renders a raw key.
 */
class DistillationPotionsTableTest {

    private static final String RESOURCE = "/assets/distillation/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/distillation/lang/en_us.json");

    /** SPEC §2: path → {base, long, strong} ticks; -1 = no level II (glowstone stays invalid). */
    private static final Map<String, int[]> SPEC_DURATIONS = Map.of(
            "resistance", new int[]{3600, 9600, 1800},
            "haste", new int[]{9600, 24000, 4800},
            "absorption", new int[]{3600, 9600, 1800},
            "luck", new int[]{9600, 24000, -1},
            "glowing", new int[]{3600, 9600, -1},
            "mining_fatigue", new int[]{3600, 9600, -1},
            "bad_luck", new int[]{9600, 24000, -1});

    /** Lines vanilla already localizes (vanilla ships every luck potion/arrow key). */
    private static final List<String> VANILLA_LOCALIZED = List.of("luck");

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySpecLineIsDeclaredWithSpecDurations() {
        assertEquals(SPEC_DURATIONS.size(), DistillationPotions.LINES.size(),
                "LINES and the spec table cover the same lines");
        for (DistillationPotions.Line line : DistillationPotions.LINES) {
            int[] spec = SPEC_DURATIONS.get(line.path());
            assertTrue(spec != null, "line not in the spec table: " + line.path());
            assertEquals(spec[0], line.baseTicks(), line.path() + " base duration");
            assertEquals(spec[1], line.longTicks(), line.path() + " long duration");
            assertEquals(spec[2], line.strongTicks(), line.path() + " strong duration");
        }
    }

    @Test
    void everyNewLineShipsAllFourDisplayNameKeys() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (DistillationPotions.Line line : DistillationPotions.LINES) {
            if (VANILLA_LOCALIZED.contains(line.name())) {
                continue;
            }
            for (String container : new String[]{"potion", "splash_potion", "lingering_potion", "tipped_arrow"}) {
                String key = "item.minecraft." + container + ".effect." + line.name();
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "potion display-name lang keys missing or blank: " + missing);
    }

    private static JsonObject lang() {
        try (InputStream in = DistillationPotionsTableTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }
}
