package com.rfizzle.distillation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rfizzle.distillation.brew.AttunedTargeting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The lingering half of the attuned-splash rule ({@code design/SPEC.md} §7). A lingering cloud applies
 * its effects to everyone standing in it every few ticks; this wraps that per-effect {@code addEffect}
 * call so a beneficial, duration-bearing effect from a <em>player's</em> cloud reaches only allies —
 * the back-line brewer's cloud stops healing the wave it was thrown to hold back. Attunement is read
 * live from the cloud's own {@link AreaEffectCloud#getOwner() owner}, which vanilla already persists in
 * the cloud's save data, so a cloud thrown before a reload keeps attuning afterward with no extra
 * state; a cloud with no player owner (dragon's breath, a dispensed potion, a witch's throw) is never
 * a player throw and stays vanilla-indiscriminate.
 *
 * <p>Only the duration-bearing {@code addEffect} call is wrapped; the instant branch keeps vanilla
 * targeting, matching the splash seam. The application loop is already gated to the server side and to
 * a once-every-five-ticks cadence in vanilla, so the added per-victim allegiance check rides that
 * existing budget rather than adding a new hot path.
 */
@Mixin(AreaEffectCloud.class)
abstract class AreaEffectCloudMixin {

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean distillation$attuneCloud(LivingEntity target, MobEffectInstance effect, Entity source,
                                             Operation<Boolean> original) {
        if (AttunedTargeting.suppresses(effect, target, ((AreaEffectCloud) (Object) this).getOwner())) {
            return false;
        }
        return original.call(target, effect, source);
    }
}
