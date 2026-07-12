package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.advancement.DistillationCriteria;
import com.rfizzle.distillation.discovery.FakePlayers;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The instant {@code distillation:cleanse} effect that every antidote carries ({@code design/SPEC.md}
 * §6): a single shared effect that strips exactly one affliction. The target is not baked into the
 * effect — it is carried per antidote in the potion's own table, as the amplifier of the antidote's
 * {@link net.minecraft.world.effect.MobEffectInstance}, which indexes {@link Antidotes} back to the
 * target {@link MobEffect}. Because every application path (drink, splash, and lingering cloud)
 * threads that amplifier through unchanged to {@link #applyEffectTick}, this one effect cures all
 * three forms with no per-form code.
 *
 * <p>Removal is a silent no-op when the target is absent or cannot be removed, matching the spec's
 * "drinking with the target absent consumes it with no other outcome" and "un-removable effects are
 * skipped silently".
 */
public final class CleanseMobEffect extends InstantenousMobEffect {

    public CleanseMobEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Application is authoritative on the server; a client-side prediction must not desync the
        // entity's effect list.
        if (entity.level().isClientSide) {
            return true;
        }
        Holder<MobEffect> target = Antidotes.targetForIndex(amplifier);
        if (target != null && entity.removeEffect(target) && entity instanceof ServerPlayer serverPlayer
                && !FakePlayers.isFakePlayer(serverPlayer) && serverPlayer.getActiveEffects().size() >= 2) {
            // Surgical (SPEC §9): the strip landed on a real player who keeps ≥2 other effects — a
            // dispenser-thrown splash curing a fake player earns nothing. The cleanse is instant
            // (applied via applyInstantenousEffect, never stored), so the active list here is exactly
            // the drinker's other effects, with the just-removed target already gone.
            DistillationCriteria.ANTIDOTE_SURGICAL.trigger(serverPlayer);
        }
        return true;
    }
}
