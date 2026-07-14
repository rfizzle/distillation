// Tier: 1 (pure JUnit — no Fabric runtime)
package com.rfizzle.distillation.item;

import com.rfizzle.distillation.item.Draughts.DrinkKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the flask's pure dose arithmetic and drink classification ({@code design/SPEC.md} §12):
 * half-unit accounting (three doses = six halves, a pending half is an odd count), the
 * compose-with-draughts sip/full/half decision, and the pour cap.
 */
class FlaskTest {

    @Test
    void aPendingHalfAlwaysFinishesFirst() {
        // An odd count carries a sipped half; it drinks as a quick DRINK_HALF whether or not the
        // drinker sneaks (SPEC §4/§12), and only ever arises from a non-instant brew.
        assertEquals(DrinkKind.DRINK_HALF, Flask.classify(1, false, true));
        assertEquals(DrinkKind.DRINK_HALF, Flask.classify(1, true, true));
        assertEquals(DrinkKind.DRINK_HALF, Flask.classify(3, false, true));
        assertEquals(DrinkKind.DRINK_HALF, Flask.classify(5, true, true));
    }

    @Test
    void aWholeDoseSipsHalfOnlyWhenSneakingANonInstantBrew() {
        assertEquals(DrinkKind.SIP_HALF, Flask.classify(6, true, true));
        assertEquals(DrinkKind.SIP_HALF, Flask.classify(4, true, true));
        assertEquals(DrinkKind.FULL, Flask.classify(6, false, true));
        assertEquals(DrinkKind.FULL, Flask.classify(2, false, true));
    }

    @Test
    void anInstantBrewNeverSips() {
        assertEquals(DrinkKind.FULL, Flask.classify(6, true, false));
        assertEquals(DrinkKind.FULL, Flask.classify(2, true, false));
    }

    @Test
    void aFullDoseSpendsTwoHalvesASipOrHalfSpendsOne() {
        assertEquals(2, Flask.halvesConsumed(DrinkKind.FULL));
        assertEquals(1, Flask.halvesConsumed(DrinkKind.SIP_HALF));
        assertEquals(1, Flask.halvesConsumed(DrinkKind.DRINK_HALF));
    }

    @Test
    void halvesAfterSpendsAndFloorsAtZero() {
        assertEquals(4, Flask.halvesAfter(6, DrinkKind.FULL));
        assertEquals(5, Flask.halvesAfter(6, DrinkKind.SIP_HALF));
        assertEquals(4, Flask.halvesAfter(5, DrinkKind.DRINK_HALF));
        assertEquals(0, Flask.halvesAfter(2, DrinkKind.FULL));
        assertEquals(0, Flask.halvesAfter(1, DrinkKind.DRINK_HALF));
    }

    @Test
    void pouringADoseAddsTwoHalvesCappedAtFull() {
        assertEquals(2, Flask.addDoseHalves(0));
        assertEquals(4, Flask.addDoseHalves(2));
        assertEquals(6, Flask.addDoseHalves(4));
        assertEquals(6, Flask.addDoseHalves(6));  // already full
        assertEquals(6, Flask.addDoseHalves(5));  // a pending half + a dose still caps at full
    }

    @Test
    void emptyAndFullBounds() {
        assertTrue(Flask.isEmpty(0));
        assertFalse(Flask.isEmpty(1));
        assertTrue(Flask.isFull(6));
        assertFalse(Flask.isFull(5));
        assertEquals(6, Flask.MAX_HALVES);
    }

    @Test
    void doseLabelParts() {
        assertEquals(3, Flask.wholeDoses(6));
        assertEquals(2, Flask.wholeDoses(5));
        assertEquals(0, Flask.wholeDoses(1));
        assertFalse(Flask.hasPendingHalf(6));
        assertTrue(Flask.hasPendingHalf(5));
        assertTrue(Flask.hasPendingHalf(1));
    }
}
