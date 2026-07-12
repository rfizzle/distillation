package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The menu-side twin of the block entity's ingredient-slot gate: the slot accepts exactly the
 * graph's ingredients, so an item whose only conversions are config-disabled can't be placed by a
 * player any more than by a hopper. Runs on both logical sides — {@code effectiveConfig()} reads
 * the synced config on a client, the local one on a server.
 */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot")
abstract class BrewingStandMenuIngredientsSlotMixin {

    @Shadow
    @Final
    private PotionBrewing potionBrewing;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void distillation$gateIngredientSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        DistillationConfig config = RecipeGraphs.effectiveConfig();
        cir.setReturnValue(RecipeGraphs
                .lookup(this.potionBrewing, config.enableMissingBrews, config.enablePremiumBrews,
                        config.enableAntidotes)
                .isIngredient(stack));
    }
}
