package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.client.render.NightVisionFadeMath;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's Night Vision expiry strobe with a smooth fade (issue #26). Vanilla's
 * {@link GameRenderer#getNightVisionScale} oscillates its returned brightness scale between 0.4 and
 * 1.0 every frame over the effect's final {@link NightVisionFadeMath#FADE_TICKS} ticks; that single
 * static value drives both the lightmap ({@code LightTexture}) and the fog ({@code FogRenderer}),
 * so overriding this one method fixes the flicker at every call site.
 *
 * <p>When {@code client.smoothNightVisionFade} is on, the inject computes a monotonic fade via the
 * pure {@link NightVisionFadeMath} and cancels vanilla's flicker math outright. When off, it never
 * cancels, so vanilla brightness renders byte-for-byte untouched — the mod's vanilla-parity
 * guarantee. This is a client-only render preference, so the local config is read directly (it is
 * excluded from the server sync); the read is a plain field access, safe on the per-frame render
 * thread with no allocation.
 */
@Mixin(GameRenderer.class)
public class GameRendererNightVisionMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void distillation$smoothFade(LivingEntity entity, float partialTick,
                                                CallbackInfoReturnable<Float> cir) {
        if (!Distillation.getConfig().client.smoothNightVisionFade) {
            return;
        }
        MobEffectInstance effect = entity.getEffect(MobEffects.NIGHT_VISION);
        if (effect == null) {
            // Vanilla's caller confirms the effect before calling; guard anyway and defer to vanilla.
            return;
        }
        cir.setReturnValue(NightVisionFadeMath.scale(effect.getDuration(), partialTick));
    }
}
