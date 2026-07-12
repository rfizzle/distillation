package com.rfizzle.distillation.batch;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The brewing stand's tick-loop internals, exposed to {@link BatchBrewTick} by the batch mixin so
 * the reimplemented server tick lives in a plain, testable class instead of the mixin. Mirrors the
 * private vanilla fields {@code items}, {@code fuel}, {@code brewTime}, {@code ingredient}, and the
 * {@code lastPotionCount} block-state cache.
 */
public interface BatchStand extends RiggedStand {

    NonNullList<ItemStack> distillation$items();

    int distillation$fuel();

    void distillation$setFuel(int fuel);

    int distillation$brewTime();

    void distillation$setBrewTime(int brewTime);

    Item distillation$ingredient();

    void distillation$setIngredient(Item ingredient);

    boolean[] distillation$lastPotionCount();

    void distillation$setLastPotionCount(boolean[] lastPotionCount);
}
