// Tier: 1 (pure JUnit — the drink-time decision is arithmetic over the classified kind, no Minecraft types)
package com.rfizzle.distillation.item;

import com.rfizzle.distillation.item.Draughts.DrinkKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the quick-sip drink time of {@code design/SPEC.md} §4: a draught is a half-measure, so a sip
 * of a full bottle or a stored half both swallow in half the vanilla drink time (⌊÷2⌋), while a full
 * drink keeps vanilla's time.
 */
class DraughtsUseDurationTest {

    private static final int VANILLA_DRINK_TICKS = 32; // vanilla PotionItem.getUseDuration

    @Test
    void fullDrinkKeepsVanillaTime() {
        assertEquals(VANILLA_DRINK_TICKS, Draughts.useDuration(DrinkKind.FULL, VANILLA_DRINK_TICKS));
    }

    @Test
    void sipHalfSwallowsInHalfTheTime() {
        assertEquals(16, Draughts.useDuration(DrinkKind.SIP_HALF, VANILLA_DRINK_TICKS));
    }

    @Test
    void drinkHalfSwallowsInHalfTheTime() {
        assertEquals(16, Draughts.useDuration(DrinkKind.DRINK_HALF, VANILLA_DRINK_TICKS));
    }

    @Test
    void halfMeasureFloorsAnOddVanillaTime() {
        // Track vanilla's own duration rather than a hardcoded 16 — an odd input floors, never rounds up.
        assertEquals(15, Draughts.useDuration(DrinkKind.DRINK_HALF, 31));
    }
}
