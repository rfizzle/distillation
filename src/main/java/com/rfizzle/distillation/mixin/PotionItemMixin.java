package com.rfizzle.distillation.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
 * halved durations by scaling vanilla's own duration factor, and a half-measure — a sip or a stored
 * half — swallows in half the drink time.
 */
@Mixin(PotionItem.class)
abstract class PotionItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void distillation$sipHalf(ItemStack stack, Level level, LivingEntity entity,
                                      CallbackInfoReturnable<ItemStack> cir) {
        Draughts.DrinkKind kind = Draughts.kindFor(stack, entity);
        if (kind == Draughts.DrinkKind.FULL) {
            return;
        }
        cir.setReturnValue(Draughts.finishDraught(stack, level, entity, kind));
    }

    /**
     * A draught is a half-measure and swallows in half the time (SPEC §4): the same decision that
     * halves the dose halves the drink duration, so a sip or a stored half both quicken while a full
     * drink keeps vanilla's time. The decision is latched at the start of the drink ({@link
     * Draughts#kindFor}), so the speed fixed here and the dose applied at completion always agree and
     * neither can be flipped by toggling the sneak mid-drink. Resolves identically on both sides —
     * the marker component and config sync to the client, and the crouch is read once at the start.
     */
    @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
    private int distillation$quickSip(int original, ItemStack stack, LivingEntity entity) {
        return Draughts.useDuration(Draughts.kindFor(stack, entity), original);
    }

    @ModifyArg(method = "appendHoverText",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/alchemy/PotionContents;addPotionTooltip(Ljava/util/function/Consumer;FF)V"),
            index = 1)
    private float distillation$halveTooltipDuration(float durationFactor, @Local(argsOnly = true) ItemStack stack) {
        return Draughts.isDraught(stack) ? durationFactor * 0.5F : durationFactor;
    }
}
