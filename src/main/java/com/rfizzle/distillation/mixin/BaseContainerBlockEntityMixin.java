package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.BatchOwnerContext;
import com.rfizzle.distillation.batch.BatchStates;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Batch ownership ({@code design/SPEC.md} §3): every write to a brewing stand's ingredient slot
 * funnels through {@code BaseContainerBlockEntity.setItem} — a player insert (via {@code Slot.set}
 * during a menu click) or a hopper insert/extract (via the {@code WorldlyContainer} path). A menu
 * click carries the player through {@link BatchOwnerContext} and records them as the batch owner; a
 * hopper carries no context and disowns the stand, so automated stands never batch-brew.
 *
 * <p>{@code setItem} is defined here, not on the brewing stand subclass, so the mixin targets this
 * class and gates to brewing stands with an {@code instanceof}.
 */
@Mixin(BaseContainerBlockEntity.class)
abstract class BaseContainerBlockEntityMixin {

    @Inject(method = "setItem", at = @At("HEAD"))
    private void distillation$trackBatchOwner(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != BatchBrew.INGREDIENT_SLOT || !((Object) this instanceof BrewingStandBlockEntity stand)) {
            return;
        }
        UUID player = BatchOwnerContext.current();
        if (player != null) {
            if (!stack.isEmpty()) {
                BatchStates.setOwner(stand, player);
            }
        } else {
            BatchStates.clearOwner(stand);
        }
    }
}
