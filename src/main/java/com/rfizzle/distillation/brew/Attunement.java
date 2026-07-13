package com.rfizzle.distillation.brew;

/**
 * The pure decision rule of the attuned-splash feature ({@code design/SPEC.md} §7): a
 * <em>beneficial, duration-bearing</em> effect thrown by a player reaches only allies, so a support
 * brewer's clouds and splashes stop buffing the enemy wave standing on their friend. Extracted from
 * {@link com.rfizzle.distillation.mixin.ThrownPotionMixin} and
 * {@link com.rfizzle.distillation.mixin.AreaEffectCloudMixin} — the same pure-core-behind-a-thin-shell
 * split {@link ThrownRebalance} uses for the §7 numbers — so the rule is unit-testable without a
 * running server and the two mixins stay config-to-vanilla shells.
 *
 * <p>Everything the rule does <em>not</em> match applies exactly as vanilla: harmful and neutral
 * effects stay indiscriminate grenades; instant effects (including beneficial Instant Health, which
 * doubles as an anti-undead weapon) keep vanilla's distance-scaled, everyone-in-range targeting;
 * a potion with no player thrower (dispensed, witch-thrown) is untouched; and the whole feature off
 * ({@code enableAttunedSplash=false}) leaves vanilla behaviorally intact.
 */
public final class Attunement {

    private Attunement() {
    }

    /**
     * Whether to <em>suppress</em> (not apply) a single splash/lingering effect on one target. True
     * only when every attunement condition holds at once: the feature is enabled, the thrower is a
     * player, the effect is beneficial, the effect is duration-bearing (not instantaneous), and the
     * target is not an ally. Any other case returns false — the effect applies as vanilla.
     *
     * @param enabled        {@code enableAttunedSplash}
     * @param ownerIsPlayer  the thrown potion / cloud was thrown by a player (attunement is the
     *                       brewer's hand — a dispenser or witch throw is not a player)
     * @param beneficial     the effect's category is {@code BENEFICIAL}
     * @param instantaneous  the effect applies instantaneously (Instant Health/Harming) rather than
     *                       over a duration
     * @param targetIsAlly   the hit entity is a player or a player's pet
     */
    public static boolean suppressBeneficial(boolean enabled, boolean ownerIsPlayer,
                                             boolean beneficial, boolean instantaneous,
                                             boolean targetIsAlly) {
        return enabled && ownerIsPlayer && beneficial && !instantaneous && !targetIsAlly;
    }
}
