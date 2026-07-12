package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.batch.BatchBottleSlot;
import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.RiggedMenu;
import com.rfizzle.distillation.batch.RiggedStand;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the batch row to vanilla's {@link BrewingStandMenu} ({@code design/SPEC.md} §3): three bottle
 * slots for the stand's grown container indices 5–7 and one extra {@code DataSlot} carrying the rig
 * flag, both auto-synced by {@code broadcastChanges} — no custom packet. The slots are inactive (so
 * hidden and unclickable) until the stand is rigged.
 *
 * <p>Server-side the slots back onto the block entity's real batch slots; client-side the vanilla
 * menu holds only a five-slot {@code SimpleContainer}, so the slots back onto a private three-slot
 * container fed by the same slot sync — the container index differs by side, but menus sync by menu
 * slot index, so the bottles arrive either way.
 */
@Mixin(BrewingStandMenu.class)
abstract class BrewingStandMenuMixin extends AbstractContainerMenu implements RiggedMenu {

    @Unique
    private boolean distillation$rigged;

    private BrewingStandMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
            at = @At("TAIL"))
    private void distillation$addBatchRow(int id, Inventory inventory, Container container, ContainerData data,
                                          CallbackInfo ci) {
        boolean server = container instanceof RiggedStand;
        Container batchContainer = server ? container : new SimpleContainer(BatchBrew.BATCH_SLOT_X.length);
        int base = server ? BatchBrew.FIRST_BATCH_SLOT : 0;
        for (int i = 0; i < BatchBrew.BATCH_SLOT_X.length; i++) {
            this.addSlot(new BatchBottleSlot(batchContainer, base + i,
                    BatchBrew.BATCH_SLOT_X[i], BatchBrew.BATCH_SLOT_Y, () -> distillation$riggedFor(container)));
        }
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return container instanceof RiggedStand rs && rs.distillation$isRigged() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                distillation$rigged = value != 0;
            }
        });
    }

    // Server reads the block entity directly; client reads the synced data slot.
    @Unique
    private boolean distillation$riggedFor(Container container) {
        return container instanceof RiggedStand rs ? rs.distillation$isRigged() : this.distillation$rigged;
    }

    @Override
    public boolean distillation$isRigged() {
        return this.distillation$rigged;
    }
}
