// Tier: 1 (pure JUnit — no MC bootstrap; plain arithmetic and boolean gates)
package com.rfizzle.distillation.arrow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure dip-count and charge-gate math of {@code design/SPEC.md} §Tipped arrows: a dip tips the
 * configured rate capped by the arrows held, and a potion may charge only when discovery is off or
 * the player has learned a conversion that produces it.
 */
class ArrowTippingTest {

    @Test
    void aDipTipsUpToTheConfiguredRate() {
        assertEquals(8, ArrowTipping.arrowsPerDip(64, 8));
    }

    @Test
    void aDipNeverTipsMoreArrowsThanHeld() {
        assertEquals(3, ArrowTipping.arrowsPerDip(3, 8));
    }

    @Test
    void anEmptyHandTipsNothing() {
        assertEquals(0, ArrowTipping.arrowsPerDip(0, 8));
    }

    @Test
    void aNonPositiveRateTipsNothing() {
        assertEquals(0, ArrowTipping.arrowsPerDip(64, 0));
        assertEquals(0, ArrowTipping.arrowsPerDip(64, -4));
    }

    @Test
    void discoveryOffLetsAnyPotionCharge() {
        assertTrue(ArrowTipping.chargeAllowed(false, false, 0));
    }

    @Test
    void aDiscoveredProducerLetsAPotionCharge() {
        assertTrue(ArrowTipping.chargeAllowed(true, true, 2));
    }

    @Test
    void anUndiscoveredPotionCannotCharge() {
        assertFalse(ArrowTipping.chargeAllowed(true, false, 2));
    }

    @Test
    void aPotionNoConversionProducesFailsClosed() {
        assertFalse(ArrowTipping.chargeAllowed(true, true, 0));
    }
}
