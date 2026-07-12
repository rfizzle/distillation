package com.rfizzle.distillation.batch;

/**
 * Duck-typed onto {@link net.minecraft.world.level.block.entity.BrewingStandBlockEntity} by the
 * batch mixin ({@code design/SPEC.md} §3): the server-side rig flag the tick loop maintains and the
 * menu's rig {@code DataSlot} reads. The flag is cached from a throttled {@link BatchRig} poll and
 * re-validated authoritatively at each pass start.
 */
public interface RiggedStand {

    boolean distillation$isRigged();

    void distillation$setRigged(boolean rigged);
}
