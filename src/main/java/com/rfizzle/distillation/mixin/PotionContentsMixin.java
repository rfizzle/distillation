package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.brew.HonestDurations;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The honest-durations seam ({@code design/SPEC.md} §4): rewrites the duration of a §4 utility
 * potion's effects wherever {@code PotionContents} yields them — {@link #forEachEffect} (drinking and
 * every one-by-one application) and {@link #getAllEffects} (tooltip rendering and thrown-potion
 * application) — so the retune is uniform and never stored in the item.
 *
 * <p>{@code getAllEffects} returns the shared registered {@code Potion}'s own effect list; the retune
 * there builds a fresh copy list and never mutates it in place, so an overridden potion's registry
 * entry is untouched. The override applies only to a plain registered potion with no custom effects,
 * so a tipped arrow or custom-effect bottle is left to vanilla.
 */
@Mixin(PotionContents.class)
abstract class PotionContentsMixin {

    @Shadow
    @Final
    private Optional<Holder<Potion>> potion;

    @Shadow
    @Final
    private List<MobEffectInstance> customEffects;

    /**
     * The §4 duration for this contents' potion, or {@code -1} when honest durations are off, the
     * bottle carries no plain registered potion, or custom effects ride along (left to vanilla).
     */
    @Unique
    private int distillation$override() {
        if (!RecipeGraphs.effectiveConfig().enableHonestDurations
                || this.potion.isEmpty() || !this.customEffects.isEmpty()) {
            return -1;
        }
        return this.potion.get().unwrapKey()
                .map(key -> HonestDurations.durationFor(key.location()))
                .orElse(-1);
    }

    @Inject(method = "forEachEffect", at = @At("HEAD"), cancellable = true)
    private void distillation$retuneForEach(Consumer<MobEffectInstance> consumer, CallbackInfo ci) {
        int ticks = distillation$override();
        if (ticks < 0) {
            return;
        }
        // customEffects is empty here (gated in the override), so this reproduces forEachEffect's
        // potion loop, handing the consumer a fresh copy at the retuned duration.
        for (MobEffectInstance base : this.potion.get().value().getEffects()) {
            consumer.accept(HonestDurations.withDuration(base, ticks));
        }
        ci.cancel();
    }

    @Inject(method = "getAllEffects", at = @At("RETURN"), cancellable = true)
    private void distillation$retuneAll(CallbackInfoReturnable<Iterable<MobEffectInstance>> cir) {
        int ticks = distillation$override();
        if (ticks < 0) {
            return;
        }
        List<MobEffectInstance> retuned = new ArrayList<>();
        for (MobEffectInstance base : cir.getReturnValue()) {
            retuned.add(HonestDurations.withDuration(base, ticks));
        }
        cir.setReturnValue(retuned);
    }
}
