// Tier: 1 (pure JUnit — resolve is arithmetic over ticks/amplifiers, no Minecraft types touched)
package com.rfizzle.distillation.brew;

import org.junit.jupiter.api.Test;

import static com.rfizzle.distillation.brew.TopUpDrinking.VANILLA_MERGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the pure top-up decision of {@code design/SPEC.md} §4: a same-strength re-drink adds the dose
 * to the running timer, clamped at 2× the full-dose duration and never reduced; a sip adds ⌊base ÷ 2⌋
 * against the same cap; a different amplifier or an infinite-duration effect defers to vanilla merge.
 */
class TopUpDrinkingTest {

    // Fire Resistance honest durations (SPEC §4): full dose 8:00, cap 16:00.
    private static final int BASE = 9600;
    private static final int CAP = 2 * BASE;

    @Test
    void sameStrengthReDrinkAddsToTheRunningTimer() {
        // 7:50 remaining + a fresh 8:00 dose = 15:50, under the 16:00 cap — the timer that vanilla
        // would have thrown away by taking the max.
        assertEquals(9400 + BASE, TopUpDrinking.resolve(9400, 0, BASE, 0, BASE));
    }

    @Test
    void theSumClampsAtTwiceTheBaseDuration() {
        // 12:00 remaining + 8:00 would be 20:00; the cap holds it at 16:00.
        assertEquals(CAP, TopUpDrinking.resolve(12000, 0, BASE, 0, BASE));
    }

    @Test
    void anAlreadyOverCapTimerIsNeverReduced() {
        // A timer already past the cap (e.g. a longer source) keeps its remainder rather than being
        // clamped down — a drink never shortens a buff.
        assertEquals(20000, TopUpDrinking.resolve(20000, 0, BASE, 0, BASE));
    }

    @Test
    void firstDoseWorthClampsButReturnsTheDoseWhenAloneAtTheSeam() {
        // resolve is only ever called with an active effect; with a tiny remainder the sum is just
        // remainder + dose (well under the cap).
        assertEquals(1 + BASE, TopUpDrinking.resolve(1, 0, BASE, 0, BASE));
    }

    @Test
    void aSipTopsUpHalfADoseAgainstTheFullCap() {
        // The sip passes its un-halved base (BASE) as the cap basis, adding ⌊base ÷ 2⌋.
        int sip = HonestDurations.half(BASE);
        assertEquals(5000 + sip, TopUpDrinking.resolve(5000, 0, sip, 0, BASE));
        // Two sips onto a nearly-full buff still clamp at 2× the full base, never 2× the sip.
        assertEquals(CAP, TopUpDrinking.resolve(CAP - 100, 0, sip, 0, BASE));
    }

    @Test
    void differentAmplifierDefersToVanillaMerge() {
        assertEquals(VANILLA_MERGE, TopUpDrinking.resolve(9400, 1, BASE, 0, BASE));
        assertEquals(VANILLA_MERGE, TopUpDrinking.resolve(9400, 0, BASE, 1, BASE));
    }

    @Test
    void anInfiniteDurationOnEitherSideDefersToVanillaMerge() {
        // MobEffectInstance.INFINITE_DURATION is -1; topping up an endless effect is meaningless.
        assertEquals(VANILLA_MERGE, TopUpDrinking.resolve(-1, 0, BASE, 0, BASE));
        assertEquals(VANILLA_MERGE, TopUpDrinking.resolve(9400, 0, -1, 0, BASE));
    }
}
