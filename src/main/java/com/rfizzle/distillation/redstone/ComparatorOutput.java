package com.rfizzle.distillation.redstone;

import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.BatchStand;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Thin shell over {@link ComparatorSignal} ({@code design/SPEC.md} §9): reads a brewing stand's live
 * state — whether a cycle is running and how many bottle slots are occupied — and defers the scale
 * math to the pure core. Bottle occupancy follows the same non-empty convention vanilla uses for the
 * {@code HAS_BOTTLE} block state, so the comparator counts exactly what the block already shows.
 */
public final class ComparatorOutput {

    private ComparatorOutput() {
    }

    /**
     * The brew-state comparator signal for a stand. The batch row (slots 5–7) counts only while the
     * stand is rigged — an unrigged stand's hidden row is never occupied, so this matches what the
     * player can see and act on.
     */
    public static int signal(BrewingStandBlockEntity be) {
        BatchStand stand = (BatchStand) be;
        NonNullList<ItemStack> items = stand.distillation$items();
        int count = countOccupied(items, 0, 2);
        if (stand.distillation$isRigged()) {
            count += countOccupied(items, BatchBrew.FIRST_BATCH_SLOT, BatchBrew.LAST_BATCH_SLOT);
        }
        // brewTime > 0 covers both the vanilla path and a rigged batch pass (BatchBrewTick drives the
        // same countdown), so it is the one "a cycle is running" test for either.
        boolean brewing = stand.distillation$brewTime() > 0;
        return ComparatorSignal.of(brewing, count);
    }

    private static int countOccupied(NonNullList<ItemStack> items, int fromInclusive, int toInclusive) {
        int count = 0;
        for (int slot = fromInclusive; slot <= toInclusive; slot++) {
            if (!items.get(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
