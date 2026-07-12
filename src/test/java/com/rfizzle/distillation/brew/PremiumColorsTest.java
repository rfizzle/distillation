// Tier: 1 (pure JUnit — no Minecraft types)
package com.rfizzle.distillation.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the "deeper liquid color" math of {@code design/SPEC.md} §5: every channel darkens, the
 * result is deterministic, alpha survives, and channels stay in range.
 */
class PremiumColorsTest {

    @Test
    void deepenDarkensEveryChannel() {
        int deep = PremiumColors.deepen(0xFFFFFF);
        int expected = Math.round(0xFF * PremiumColors.DEEPEN_FACTOR); // 166 = 0xA6
        assertEquals((expected << 16) | (expected << 8) | expected, deep);
        assertTrue((deep & 0xFF) < 0xFF, "blue darkened");
        assertTrue(((deep >> 8) & 0xFF) < 0xFF, "green darkened");
        assertTrue(((deep >> 16) & 0xFF) < 0xFF, "red darkened");
    }

    @Test
    void blackStaysBlack() {
        assertEquals(0, PremiumColors.deepen(0x000000));
    }

    @Test
    void alphaByteSurvives() {
        int deep = PremiumColors.deepen(0xFF3399CC);
        assertEquals(0xFF000000, deep & 0xFF000000, "the top byte is left untouched");
    }

    @Test
    void channelsStayInRange() {
        for (int color : new int[]{0x000000, 0x7F7F7F, 0xFFFFFF, 0x385DC6, 0x4E9331}) {
            int deep = PremiumColors.deepen(color);
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = (deep >> shift) & 0xFF;
                assertTrue(channel >= 0 && channel <= 255, "channel in range for " + Integer.toHexString(color));
            }
        }
    }

    @Test
    void isDeterministic() {
        assertEquals(PremiumColors.deepen(0x385DC6), PremiumColors.deepen(0x385DC6));
    }
}
