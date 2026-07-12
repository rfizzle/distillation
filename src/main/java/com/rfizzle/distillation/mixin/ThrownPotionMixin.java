package com.rfizzle.distillation.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.ThrownRebalance;
import com.rfizzle.distillation.config.DistillationConfig;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The splash &amp; lingering rebalance seam ({@code design/SPEC.md} §7). Both hooks target the vanilla
 * thrown-potion entity — the single convergence point for player-, dispenser-, and witch-thrown
 * potions — and both run only on its server-side {@code onHit} path, so they read
 * {@link Distillation#getConfig()} directly. Neither touches {@code AreaEffectCloud} itself, so
 * clouds from other sources (a dragon's breath attack, a charged creeper, a directly spawned cloud)
 * keep vanilla numbers; the rebalance reaches only clouds a thrown lingering potion makes.
 *
 * <p>When {@code enableThrownRebalance} is off, each hook is inert (the splash arg passes through
 * unchanged; the cloud hook returns before touching anything), leaving behaviorally untouched
 * vanilla — including vanilla's 100%-at-point-blank, distance-scaled splash, which no value of the
 * flat {@code splashDurationFactor} could reproduce.
 */
@Mixin(ThrownPotion.class)
abstract class ThrownPotionMixin {

    /**
     * Splash: replace vanilla's distance-scaled duration operator with a flat one, so every
     * duration-bearing effect a splash lands applies {@code splashDurationFactor} of its drinkable
     * duration regardless of hit distance. Instant effects never reach this call (they take the
     * {@code applyInstantenousEffect} branch), so they keep vanilla's distance-scaled potency.
     */
    @ModifyArg(method = "applySplash",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;mapDuration(Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)I"),
            index = 0)
    private Int2IntFunction distillation$flatSplashDuration(Int2IntFunction original) {
        DistillationConfig config = Distillation.getConfig();
        if (!config.enableThrownRebalance) {
            return original;
        }
        float factor = config.splashDurationFactor;
        return base -> ThrownRebalance.splashDuration(base, factor);
    }

    /**
     * Lingering: after vanilla has built and configured the cloud, overwrite its lifetime, starting
     * radius, and linear per-tick shrink with the configured numbers. Radius-on-use (−0.5 per pickup)
     * and wait time stay vanilla — the SPEC leaves the pickup cost alone.
     */
    @Inject(method = "makeAreaOfEffectCloud", at = @At("TAIL"))
    private void distillation$rebalanceCloud(PotionContents contents, CallbackInfo ci,
                                             @Local AreaEffectCloud cloud) {
        DistillationConfig config = Distillation.getConfig();
        if (!config.enableThrownRebalance) {
            return;
        }
        int duration = config.lingeringCloudDurationTicks;
        float radius = config.lingeringCloudRadius;
        cloud.setDuration(duration);
        cloud.setRadius(radius);
        cloud.setRadiusPerTick(ThrownRebalance.cloudRadiusPerTick(radius, duration));
    }
}
