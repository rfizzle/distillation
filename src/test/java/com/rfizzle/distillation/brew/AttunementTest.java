// Tier: 1 (pure JUnit — Attunement is a boolean rule with no net.minecraft.* types)
package com.rfizzle.distillation.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the pure decision rule of the attuned-splash feature ({@code design/SPEC.md} §7): a beneficial,
 * duration-bearing effect from a player's throw is suppressed on a non-ally, and nothing else is. The
 * five inputs give 32 combinations; these cases nail the one that suppresses and each single deviation
 * that must not.
 */
class AttunementTest {

    // The one combination that suppresses: enabled, player thrower, beneficial, duration-bearing, non-ally.
    @Test
    void suppressesABeneficialDurationEffectFromAPlayerOnANonAlly() {
        assertTrue(Attunement.suppressBeneficial(true, true, true, false, false),
                "a player's beneficial duration brew must not reach a non-ally");
    }

    // Each single deviation from that combination must let the effect through.

    @Test
    void appliesWhenDisabled() {
        assertFalse(Attunement.suppressBeneficial(false, true, true, false, false),
                "feature off restores vanilla indiscriminate targeting");
    }

    @Test
    void appliesWhenThrowerIsNotAPlayer() {
        assertFalse(Attunement.suppressBeneficial(true, false, true, false, false),
                "a dispensed or witch-thrown brew is not the brewer's hand — no attunement");
    }

    @Test
    void appliesWhenEffectIsNotBeneficial() {
        // Harmful and neutral both read as beneficial=false — a grenade stays a grenade.
        assertFalse(Attunement.suppressBeneficial(true, true, false, false, false),
                "a harmful or neutral effect is never withheld from a non-ally");
    }

    @Test
    void appliesWhenEffectIsInstantaneous() {
        // Beneficial Instant Health keeps vanilla targeting — it still hurts undead.
        assertFalse(Attunement.suppressBeneficial(true, true, true, true, false),
                "instant effects keep vanilla's indiscriminate targeting");
    }

    @Test
    void appliesWhenTargetIsAnAlly() {
        assertFalse(Attunement.suppressBeneficial(true, true, true, false, true),
                "an ally is exactly who a beneficial brew is meant to reach");
    }

    // The intended positive case in full: a player buffing themselves or a pet is never suppressed.
    @Test
    void aPlayerBuffingAnAllyIsNeverSuppressed() {
        assertFalse(Attunement.suppressBeneficial(true, true, true, false, true));
    }
}
