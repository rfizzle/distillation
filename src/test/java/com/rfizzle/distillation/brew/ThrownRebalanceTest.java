// Tier: 1 (pure JUnit — ThrownRebalance is plain arithmetic with no net.minecraft.* types)
package com.rfizzle.distillation.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the pure arithmetic of the splash &amp; lingering rebalance ({@code design/SPEC.md} §7): the
 * flat splash duration (round-half-up, matching vanilla's {@code (int)(e·duration + 0.5)}) and the
 * linear cloud shrink {@code -radius / duration}.
 */
class ThrownRebalanceTest {

    @Test
    void splashDurationAppliesTheFlatFactor() {
        // The SPEC default: 87.5% of a drinkable duration, flat.
        assertEquals(3150, ThrownRebalance.splashDuration(3600, 0.875F), "0.875 · 3600");
        assertEquals(8400, ThrownRebalance.splashDuration(9600, 0.875F), "0.875 · 9600 (an honest utility line)");
    }

    @Test
    void splashDurationRoundsHalfUpLikeVanilla() {
        // (int)(0.5·3 + 0.5) = (int)2.0 = 2; the +0.5 is vanilla's own rounding, not a truncation.
        assertEquals(2, ThrownRebalance.splashDuration(3, 0.5F));
        assertEquals(1, ThrownRebalance.splashDuration(1, 0.5F));
        assertEquals(0, ThrownRebalance.splashDuration(0, 0.875F));
    }

    @Test
    void splashDurationAtRangeBounds() {
        // The clamped config keeps the factor within [0.5, 1.0]; both ends are exact.
        assertEquals(1000, ThrownRebalance.splashDuration(1000, 1.0F), "1.0 keeps the full duration");
        assertEquals(500, ThrownRebalance.splashDuration(1000, 0.5F), "0.5 halves it");
    }

    @Test
    void cloudRadiusPerTickShrinksLinearlyToZero() {
        // SPEC 4.5 / 1200 tapers to zero exactly at end of life; vanilla 3.0 / 600 is the baseline.
        assertEquals(-0.00375F, ThrownRebalance.cloudRadiusPerTick(4.5F, 1200), 1e-7F);
        assertEquals(-0.005F, ThrownRebalance.cloudRadiusPerTick(3.0F, 600), 1e-7F);
    }

    @Test
    void cloudRadiusPerTickGuardsNonPositiveDuration() {
        // Never reachable through the clamped config (lifetime floors at 600), but must not divide by zero.
        assertEquals(0.0F, ThrownRebalance.cloudRadiusPerTick(4.5F, 0), 0.0F);
        assertEquals(0.0F, ThrownRebalance.cloudRadiusPerTick(4.5F, -10), 0.0F);
    }
}
