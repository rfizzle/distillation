package com.rfizzle.distillation.batch;

/**
 * Duck-typed onto vanilla {@link net.minecraft.world.inventory.BrewingStandMenu} by the batch mixin:
 * the client-visible rig flag, synced from the server through an extra menu {@code DataSlot}. The
 * brewing screen reads it to show or hide the batch row.
 */
public interface RiggedMenu {

    boolean distillation$isRigged();
}
