package com.rfizzle.distillation.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the pure fade curve that replaces Night Vision's expiry flicker (issue #26). No
 * Minecraft bootstrap: the mixin reads the effect's remaining duration and this class answers the
 * brightness scale, so the flicker fix lives in one testable place.
 */
class NightVisionFadeMathTest {

    @Test
    void holdsFullBrightnessBeforeTheFadeWindow() {
        assertEquals(1.0F, NightVisionFadeMath.scale(300, 0.0F));
        // The window opens exactly at FADE_TICKS remaining; still full there, so the ramp meets the
        // plateau continuously with no jump.
        assertEquals(1.0F, NightVisionFadeMath.scale(NightVisionFadeMath.FADE_TICKS, 0.0F));
    }

    @Test
    void rampsLinearlyDownAcrossTheWindow() {
        assertEquals(0.5F, NightVisionFadeMath.scale(100, 0.0F));
        assertEquals(0.25F, NightVisionFadeMath.scale(50, 0.0F));
        // Partial tick interpolates within the ramp: 99.5 / 200.
        assertEquals(99.5F / 200.0F, NightVisionFadeMath.scale(100, 0.5F));
    }

    @Test
    void reachesZeroAtExpiryAndClampsBelow() {
        assertEquals(0.0F, NightVisionFadeMath.scale(0, 0.0F));
        assertEquals(0.0F, NightVisionFadeMath.scale(-40, 0.0F));
        // A late partial tick can push remaining below zero; still clamped, never negative.
        assertEquals(0.0F, NightVisionFadeMath.scale(0, 0.9F));
    }

    @Test
    void isMonotonicNonIncreasing() {
        // The whole point of the fix: no oscillation. A tick-by-tick sweep across the window (and
        // past it) must never brighten as the effect drains — a property vanilla's sin curve fails.
        float previous = NightVisionFadeMath.scale(400, 0.0F);
        for (int duration = 400; duration >= 0; duration--) {
            float current = NightVisionFadeMath.scale(duration, 0.0F);
            assertTrue(current <= previous,
                    "scale rose from " + previous + " to " + current + " at duration " + duration);
            previous = current;
        }
    }

    @Test
    void staysWithinUnitRange() {
        for (int duration = -10; duration <= 420; duration++) {
            float scale = NightVisionFadeMath.scale(duration, 0.0F);
            assertTrue(scale >= 0.0F && scale <= 1.0F, "scale out of range: " + scale);
        }
    }
}
