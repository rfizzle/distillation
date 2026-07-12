package com.rfizzle.distillation.batch;

/**
 * Duck-typed onto {@link net.minecraft.world.level.block.entity.BrewingStandBlockEntity} by the
 * batch mixin ({@code design/SPEC.md} §3): the server-side rig flag the tick loop maintains and the
 * menu's rig {@code DataSlot} reads. The flag is refreshed from a {@link BatchRig} poll each server
 * tick (two block-state reads) and is the same detection a pass start relies on.
 */
public interface RiggedStand {

    boolean distillation$isRigged();

    void distillation$setRigged(boolean rigged);
}
