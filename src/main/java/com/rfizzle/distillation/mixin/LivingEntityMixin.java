package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.item.DraughtDrinker;
import com.rfizzle.distillation.item.Draughts;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Latches the draught decision at the moment a drink starts ({@code design/SPEC.md} §4). Vanilla
 * fixes the drink's completion tick from {@code getUseDuration} at {@code startUsingItem} and never
 * re-reads it, so pairing the sip/full choice to that same instant keeps the shortened drink time
 * and the halved dose in agreement even if the drinker releases their sneak mid-drink. {@link
 * Draughts#kindFor(ItemStack, LivingEntity)} reads this back for both potion seams; a null latch (no
 * potion in use, or a direct {@code finishUsingItem} call that never started a use) falls back to a
 * live classification.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements DraughtDrinker {

    @Unique
    @Nullable
    private Draughts.DrinkKind distillation$latchedKind;

    @Override
    @Nullable
    public Draughts.DrinkKind distillation$drinkKind() {
        return this.distillation$latchedKind;
    }

    @Inject(method = "startUsingItem", at = @At("HEAD"))
    private void distillation$latchDrinkKind(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack stack = self.getItemInHand(hand);
        this.distillation$latchedKind = stack.getItem() instanceof PotionItem
                ? Draughts.classify(stack, self)
                : null;
    }
}
