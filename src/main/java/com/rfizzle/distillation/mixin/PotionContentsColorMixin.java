package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.brew.PremiumColors;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The deeper-color seam of {@code design/SPEC.md} §5: a concentrated or premium potion has stats
 * identical to (or a level-up of) its base, so vanilla's effect-derived tint would be
 * indistinguishable — this darkens {@link PotionContents#getColor()} for the concentrated/premium
 * family, derived at read and never stored, so the deepening reaches the item liquid layer, the
 * splash cloud, and the §1 vapor hint (all read this same accessor) alike.
 *
 * <p>The same accessor also tints an antidote ({@code design/SPEC.md} §6) with the color of the
 * affliction it cures — the shared cleanse effect carries no per-cure color, so the tint is read
 * from the target effect here, so a Poison Antidote reads green and a Wither Antidote black.
 *
 * <p>Neither branch is gated on its feature flag: the marking is identity-bound, so an existing
 * bottle keeps its color even with the feature off. The guard is a cheap family/antidote membership
 * on the unwrapped potion key, so a vanilla potion's color pays nothing.
 */
@Mixin(PotionContents.class)
abstract class PotionContentsColorMixin {

    @Shadow
    @Final
    private Optional<Holder<Potion>> potion;

    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true)
    private void distillation$deepenConcentrated(CallbackInfoReturnable<Integer> cir) {
        if (this.potion.isEmpty()) {
            return;
        }
        ResourceLocation id = this.potion.get().unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return;
        }
        if (PremiumBrews.isConcentrated(id)) {
            cir.setReturnValue(PremiumColors.deepen(cir.getReturnValueI()));
            return;
        }
        Holder<MobEffect> cured = Antidotes.targetForPotion(id);
        if (cured != null) {
            cir.setReturnValue(cured.value().getColor());
        }
    }
}
