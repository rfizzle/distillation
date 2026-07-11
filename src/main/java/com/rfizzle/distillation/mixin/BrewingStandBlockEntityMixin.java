package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.recipe.BrewSeam;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes the brewing stand through the recipe graph ({@code design/SPEC.md} §1): brew completion
 * resolves per bottle in {@link BrewSeam} — the one choke point — and the cycle-start and
 * ingredient-slot gates read the graph, so conversions removed by config neither brew nor start
 * cycles. With every Distillation conversion in the graph these paths are behaviorally identical
 * to the vanilla code they replace.
 */
@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {

    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void distillation$brewThroughSeam(Level level, BlockPos pos, NonNullList<ItemStack> items,
                                                     CallbackInfo ci) {
        ci.cancel();
        BrewSeam.completeBrew(level, pos, items);
    }

    @Inject(method = "isBrewable", at = @At("HEAD"), cancellable = true)
    private static void distillation$gateCycleStart(PotionBrewing brewing, NonNullList<ItemStack> items,
                                                    CallbackInfoReturnable<Boolean> cir) {
        // Only vanilla's serverTick calls this, so the local (server) config is authoritative.
        cir.setReturnValue(BrewSeam.isBrewable(
                RecipeGraphs.lookup(brewing, Distillation.getConfig().enableMissingBrews), items));
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void distillation$gateIngredientSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != 3) {
            return; // bottle and fuel slots stay vanilla
        }
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null) {
            return; // detached block entity: vanilla's PotionBrewing.EMPTY fallback rejects everything
        }
        cir.setReturnValue(RecipeGraphs.forLevel(level).isIngredient(stack));
    }
}
