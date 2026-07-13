package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.redstone.ComparatorOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the brewing stand a comparator signal that reads brew <em>state</em> ({@code design/SPEC.md}
 * §9). Vanilla already emits a signal here, but it is generic container fullness — indistinguishable
 * between a stand mid-cycle and a finished one. When the feature is on, this returns the two-band
 * working/done scale instead ({@link ComparatorOutput}); when off it falls through untouched, so the
 * stand keeps vanilla's exact fullness signal and the toggle-off = vanilla guarantee holds.
 *
 * <p>Only {@code getAnalogOutputSignal} is intercepted — vanilla's {@code hasAnalogOutputSignal}
 * already returns true, which is what we want in both modes. The comparator refresh needs no new
 * plumbing: vanilla's own {@code setChanged()} cascade already fires {@code updateNeighbourForOutput​Signal}
 * on every brew tick, at brew start, at completion (the working→done edge), and on every slot write.
 */
@Mixin(BrewingStandBlock.class)
abstract class BrewingStandBlockMixin {

    @Inject(method = "getAnalogOutputSignal", at = @At("HEAD"), cancellable = true)
    private void distillation$brewStateSignal(BlockState state, Level level, BlockPos pos,
                                              CallbackInfoReturnable<Integer> cir) {
        if (!Distillation.getConfig().enableComparatorOutput) {
            return; // feature off: leave vanilla's container-fullness signal exactly as-is
        }
        if (level.isClientSide()) {
            return; // brew state is server-authoritative; never read the (untracked) client copy
        }
        if (!(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity be)) {
            return; // missing/mismatched block entity (chunk edges, unload races): vanilla returns 0
        }
        cir.setReturnValue(ComparatorOutput.signal(be));
    }
}
