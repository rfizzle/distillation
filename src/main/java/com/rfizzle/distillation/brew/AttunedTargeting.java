package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.Distillation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The Minecraft-typed shell for the attuned-splash rule ({@code design/SPEC.md} §7), shared by the
 * splash seam ({@link com.rfizzle.distillation.mixin.ThrownPotionMixin}) and the lingering-cloud seam
 * ({@link com.rfizzle.distillation.mixin.AreaEffectCloudMixin}). It resolves the live config, the
 * effect's category, and the target's allegiance from game types, then defers the actual decision to
 * the pure {@link Attunement} rule — keeping the game-facing plumbing in one place while the logic
 * stays unit-testable.
 */
public final class AttunedTargeting {

    private AttunedTargeting() {
    }

    /**
     * Whether this effect application should be skipped because a player's beneficial, duration-bearing
     * brew is landing on a non-ally. Reads {@code enableAttunedSplash} live; a null owner (a dispensed
     * or otherwise ownerless throw) or a non-player owner (a witch's throw) is never a player, so the
     * throw stays vanilla-indiscriminate.
     *
     * @param effect the effect instance vanilla is about to apply (its duration may already be the
     *               §4 honest-duration retune — only the effect's category, unchanged by that retune,
     *               is read here)
     * @param target the entity vanilla is applying the effect to
     * @param owner  the thrown potion's / cloud's owner ({@code getOwner()}), or null
     */
    public static boolean suppresses(MobEffectInstance effect, LivingEntity target, @Nullable Entity owner) {
        boolean enabled = Distillation.getConfig().enableAttunedSplash;
        boolean ownerIsPlayer = owner instanceof Player;
        MobEffect mobEffect = effect.getEffect().value();
        boolean beneficial = mobEffect.isBeneficial();
        boolean instantaneous = mobEffect.isInstantenous();
        // Allegiance resolution is the one costly term (an owner→online-player lookup run per victim),
        // so gate it behind the cheap booleans: the rule can only fire on a beneficial, duration-bearing
        // effect from a player thrower, so if any of those misses — the feature is off, a dispenser threw
        // it, the effect is harmful/neutral, or it is instant — there is nothing to resolve and the throw
        // stays vanilla with no extra work. The pure rule keeps the same terms (so it stays independently
        // testable); this guard is the same logic, ordered for the hot path.
        if (!enabled || !ownerIsPlayer || !beneficial || instantaneous) {
            return false;
        }
        return Attunement.suppressBeneficial(enabled, ownerIsPlayer, beneficial, instantaneous, isAlly(target));
    }

    /**
     * An ally is a player or a player's pet. "Their pets" resolves through vanilla ownership only
     * ({@link OwnableEntity}, whose default {@code getOwner()} resolves the stored owner UUID to an
     * online player) — no faction system, no sibling dependency. Every other entity (hostile, passive
     * animal, villager, golem, or a pet whose owner is offline) is not an ally.
     */
    public static boolean isAlly(LivingEntity target) {
        if (target instanceof Player) {
            return true;
        }
        return target instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player;
    }
}
