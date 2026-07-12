package com.rfizzle.distillation.batch;

import com.rfizzle.distillation.discovery.DiscoveryManager;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/**
 * A batch-row bottle slot ({@code design/SPEC.md} §3): it accepts exactly what a vanilla bottle slot
 * accepts, holds a single bottle, records discovery when its brewed output is taken (the same hook a
 * bottom-row bottle uses), and is active only while the stand is rigged — an unrigged stand hides
 * the row, so the slot can be neither seen nor clicked.
 */
public class BatchBottleSlot extends Slot {

    private final BooleanSupplier rigged;

    public BatchBottleSlot(Container container, int slot, int x, int y, BooleanSupplier rigged) {
        super(container, slot, x, y);
        this.rigged = rigged;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isActive() {
        return this.rigged.getAsBoolean();
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        DiscoveryManager.onOutputTaken(player, this.container, this.getContainerSlot(), stack);
        super.onTake(player, stack);
    }
}
