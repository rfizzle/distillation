// Tier: 1 (pure JUnit — classify is a decision over booleans, no Minecraft types touched)
package com.rfizzle.distillation.item;

import com.rfizzle.distillation.item.Draughts.DrinkKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the pure sip/drink decision of {@code design/SPEC.md} §4: a full non-instant potion sips
 * only when sipping is enabled and the drinker is sneaking; a marked half always drinks its
 * remaining half (even with sipping off); instants and effectless bottles never sip.
 */
class DraughtsClassifyTest {

    @Test
    void fullPotionSneakingSipsHalf() {
        assertEquals(DrinkKind.SIP_HALF, Draughts.classify(false, true, true, true));
    }

    @Test
    void notSneakingDrinksFull() {
        assertEquals(DrinkKind.FULL, Draughts.classify(false, true, false, true));
    }

    @Test
    void sippingDisabledDrinksFull() {
        assertEquals(DrinkKind.FULL, Draughts.classify(false, false, true, true));
    }

    @Test
    void instantOrEffectlessPotionDrinksFull() {
        // No non-instant effect (an instant potion or a water bottle): sneaking cannot sip it.
        assertEquals(DrinkKind.FULL, Draughts.classify(false, true, true, false));
    }

    @Test
    void markedHalfAlwaysDrinksItsHalf() {
        assertEquals(DrinkKind.DRINK_HALF, Draughts.classify(true, true, false, true));
        // Even with sipping disabled and not sneaking, an existing half stays drinkable.
        assertEquals(DrinkKind.DRINK_HALF, Draughts.classify(true, false, false, false));
    }
}
