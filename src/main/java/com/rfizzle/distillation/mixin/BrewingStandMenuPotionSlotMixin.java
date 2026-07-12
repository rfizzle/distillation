package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.Draughts;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The discovery extraction hook ({@code design/SPEC.md} §1): taking a brewed output from a bottle
 * slot records the conversion that produced it for the taking player. Hoppers pull through the
 * raw container interface and never reach {@code Slot#onTake}, so automation teaches nobody by
 * construction. Runs after vanilla's own take handling (stats, brewed-potion criteria). Also
 * rejects a half draught from the bottle slots ({@code design/SPEC.md} §4) — it is not a receptive
 * bottle, so the stand never tops it up.
 */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
abstract class BrewingStandMenuPotionSlotMixin extends Slot {

    // Stub constructor — satisfies Java, never called.
    private BrewingStandMenuPotionSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void distillation$recordDiscovery(Player player, ItemStack stack, CallbackInfo ci) {
        DiscoveryManager.onOutputTaken(player, this.container, this.getContainerSlot(), stack);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void distillation$rejectDraught(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Draughts.isDraught(stack)) {
            cir.setReturnValue(false);
        }
    }
}
