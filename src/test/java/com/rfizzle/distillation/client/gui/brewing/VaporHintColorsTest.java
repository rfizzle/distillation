package com.rfizzle.distillation.client.gui.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier 1 — the vapor-hint blend of {@code design/SPEC.md} §1: distinct output colors average into
 * one tint, and the render opacity is a fixed 60%.
 */
class VaporHintColorsTest {

    @Test
    void emptyBlendsToZero() {
        assertEquals(0, VaporHintColors.blend(new int[0]));
    }

    @Test
    void singleColorPassesThrough() {
        assertEquals(0xFF0000, VaporHintColors.blend(new int[]{0xFF0000}));
        assertEquals(0x3C6EC8, VaporHintColors.blend(new int[]{0x3C6EC8}));
    }

    @Test
    void distinctColorsAverageEachChannel() {
        // red + blue → mid magenta: r=(255+0)/2=127, g=0, b=(0+255)/2=127.
        assertEquals(0x7F007F, VaporHintColors.blend(new int[]{0xFF0000, 0x0000FF}));
        // three greys average to a grey.
        assertEquals(0x202020, VaporHintColors.blend(new int[]{0x000000, 0x303030, 0x303030}));
    }

    @Test
    void channelExtractionIsNormalized() {
        assertEquals(1.0F, VaporHintColors.red(0xFF8040), 1.0E-6);
        assertEquals(0x80 / 255.0F, VaporHintColors.green(0xFF8040), 1.0E-6);
        assertEquals(0x40 / 255.0F, VaporHintColors.blue(0xFF8040), 1.0E-6);
    }

    @Test
    void hintOpacityIsSixtyPercent() {
        assertEquals(0.6F, VaporHintColors.HINT_OPACITY, 1.0E-6);
    }
}
