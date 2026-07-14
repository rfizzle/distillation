package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.item.FlaskItem;
import com.rfizzle.distillation.item.FlaskPour;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pour seam ({@code design/SPEC.md} §12): right-click-using a potion in the main hand with a
 * flask in the off hand pours a dose into the flask instead of drinking, delegating to {@link
 * FlaskPour}. Splash and lingering potions ({@code ThrowablePotionItem}) override {@code use} to
 * throw, so this base-class inject never fires for them — only normal potions pour. A flask in the
 * main hand drinks through the flask's own {@code use}; this seam only intercepts the potion side. On
 * any non-pour condition {@link FlaskPour#tryPour} returns null and the drink proceeds untouched, so
 * {@code enableFlask=false} (and every ineligible case) is byte-identical to vanilla.
 */
@Mixin(PotionItem.class)
abstract class PotionItemPourMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void distillation$pourIntoFlask(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof FlaskItem)) {
            return;
        }
        InteractionResultHolder<ItemStack> result =
                FlaskPour.tryPour(level, player, hand, player.getItemInHand(hand), offhand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
