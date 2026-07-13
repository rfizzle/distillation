package com.rfizzle.distillation.brew;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Top-up drinking ({@code design/SPEC.md} §4): re-drinking a brew whose effect is already running
 * <em>adds</em> the dose to the remaining timer instead of resetting it, so a bottle drunk with time
 * left is not wasted. The running total is capped at 2× the brew's full-dose duration and never
 * reduced below what is already there; a sip adds ⌊base ÷ 2⌋ against the same cap.
 *
 * <p>The top-up applies only between doses of the <em>same strength</em> — a different amplifier, or
 * an infinite-duration effect on either side, defers to vanilla's own merge ({@link #VANILLA_MERGE}).
 * The pure decision ({@link #resolve}) is unit-tested; the thin shell ({@link #resolveInstance})
 * reads the drinker's live effect and hands back the instance to apply, deferring to vanilla merge by
 * returning the dose unchanged.
 *
 * <p>Both drink seams — the full-drink {@code addEffect} inside {@code PotionItem}'s effect applier
 * ({@link com.rfizzle.distillation.mixin.PotionItemMixin}) and the sip
 * ({@link com.rfizzle.distillation.item.Draughts#finishDraught}) — feed this class; splash, lingering,
 * beacon, conduit, and food never reach either seam, so only bottles top up bottles.
 */
public final class TopUpDrinking {

    /**
     * Sentinel from {@link #resolve}: apply the incoming dose unchanged and let vanilla's own
     * {@code MobEffectInstance.update} merge decide (no active same-strength effect, a different
     * amplifier, or an infinite-duration effect on either side).
     */
    public static final int VANILLA_MERGE = -1;

    private TopUpDrinking() {
    }

    /**
     * The duration a drink should apply given the running effect and the incoming dose, or
     * {@link #VANILLA_MERGE} to defer to vanilla merge. Same amplifier tops up:
     * {@code max(existing, min(existing + dose, 2 · baseFullDose))} — the sum, clamped at twice the
     * full-dose duration and never below the running remainder. A different amplifier, or an infinite
     * duration ({@code < 0}) on either side, defers.
     *
     * @param existingDuration  remaining ticks on the running effect
     * @param existingAmplifier its amplifier
     * @param doseDuration      the ticks this drink applies (a full dose, or ⌊base ÷ 2⌋ for a sip)
     * @param doseAmplifier     the drink's amplifier
     * @param baseFullDose      the full-dose duration of one whole bottle of this brew (the cap basis)
     */
    public static int resolve(int existingDuration, int existingAmplifier,
                              int doseDuration, int doseAmplifier, int baseFullDose) {
        if (existingAmplifier != doseAmplifier || existingDuration < 0 || doseDuration < 0) {
            return VANILLA_MERGE;
        }
        int cap = 2 * baseFullDose;
        return Math.max(existingDuration, Math.min(existingDuration + doseDuration, cap));
    }

    /**
     * The instance a drink seam should hand to {@code addEffect}: the dose extended by the running
     * timer when a same-strength effect is active, else the dose unchanged (first drink, different
     * amplifier, or an infinite effect — all left to vanilla merge). Reads the drinker's live effect;
     * call only on the server, inside the drink's own {@code !level.isClientSide} guard.
     *
     * @param baseFullDose the full-dose duration of one whole bottle of this brew (a sip passes its
     *                     un-halved base here so the 2×-base cap is identical for sips and full drinks)
     */
    public static MobEffectInstance resolveInstance(LivingEntity entity, MobEffectInstance dose, int baseFullDose) {
        MobEffectInstance active = entity.getEffect(dose.getEffect());
        if (active == null) {
            return dose;
        }
        int resolved = resolve(active.getDuration(), active.getAmplifier(),
                dose.getDuration(), dose.getAmplifier(), baseFullDose);
        return resolved == VANILLA_MERGE ? dose : HonestDurations.withDuration(dose, resolved);
    }
}
