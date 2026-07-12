package com.rfizzle.distillation.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.distillation.item.Draughts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The sip-half drink seam ({@code design/SPEC.md} §4). Sneaking on a full, non-instant potion — or
 * drinking a marked half — is intercepted and finished through {@link Draughts}; every other drink
 * falls through to untouched vanilla. Splash and lingering potions ({@code ThrowablePotionItem},
 * itself a {@code PotionItem} subclass) override {@code use} to throw on the spot, so they never
 * enter the drink flow this seam intercepts ("cannot be sipped"). A half draught's tooltip shows
 * halved durations by scaling vanilla's own duration factor.
 */
@Mixin(PotionItem.class)
abstract class PotionItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void distillation$sipHalf(ItemStack stack, Level level, LivingEntity entity,
                                      CallbackInfoReturnable<ItemStack> cir) {
        Draughts.DrinkKind kind = Draughts.classify(stack, entity);
        if (kind == Draughts.DrinkKind.FULL) {
            return;
        }
        cir.setReturnValue(Draughts.finishDraught(stack, level, entity, kind));
    }

    @ModifyArg(method = "appendHoverText",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/alchemy/PotionContents;addPotionTooltip(Ljava/util/function/Consumer;FF)V"),
            index = 1)
    private float distillation$halveTooltipDuration(float durationFactor, @Local(argsOnly = true) ItemStack stack) {
        return Draughts.isDraught(stack) ? durationFactor * 0.5F : durationFactor;
    }
}
