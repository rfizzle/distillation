package com.rfizzle.distillation.batch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The heated-cauldron batch rig ({@code design/SPEC.md} §3): a brewing stand is rigged when a water
 * cauldron (≥1 level) sits directly below it and a heat source sits directly below the cauldron.
 * Pure detection over block states — the same call backs the tick-loop poll, the pass-start
 * re-check, and {@code /distillation rig}. No world mutation lives here.
 */
public final class BatchRig {

    private BatchRig() {
    }

    /** The six heat sources that light the rig, with the name {@code /distillation rig} reports. */
    public enum Heat {
        CAMPFIRE("campfire"),
        SOUL_CAMPFIRE("soul_campfire"),
        FIRE("fire"),
        SOUL_FIRE("soul_fire"),
        LAVA("lava"),
        MAGMA("magma");

        private final String key;

        Heat(String key) {
            this.key = key;
        }

        /** Lang suffix under {@code command.distillation.rig.heat.*}. */
        public String translationKey() {
            return "command.distillation.rig.heat." + key;
        }
    }

    /** Which piece of the rig is present or first missing, top-down. */
    public enum Piece {
        RIGGED,
        NO_CAULDRON,
        NO_WATER,
        NO_HEAT
    }

    /**
     * A rig reading: the first missing piece (or {@link Piece#RIGGED}), the cauldron water level and
     * its max when a water cauldron is present, and the heat source when one is lit.
     */
    public record Status(Piece piece, int waterLevel, int maxWater, @Nullable Heat heat) {
        public boolean rigged() {
            return piece == Piece.RIGGED;
        }
    }

    /**
     * Reads the rig below {@code standPos}: the block one below is the cauldron, the block two below
     * is the heat source. The top-down order means the reported missing piece matches how a player
     * builds the rig upward.
     */
    public static Status detect(BlockGetter level, BlockPos standPos) {
        BlockState cauldron = level.getBlockState(standPos.below());
        if (!isCauldron(cauldron)) {
            return new Status(Piece.NO_CAULDRON, 0, 0, null);
        }
        if (!cauldron.is(Blocks.WATER_CAULDRON)) {
            // A lava, powder-snow, or empty cauldron holds no water.
            return new Status(Piece.NO_WATER, 0, LayeredCauldronBlock.MAX_FILL_LEVEL, null);
        }
        int waterLevel = cauldron.getValue(LayeredCauldronBlock.LEVEL);
        Heat heat = heatSource(level.getBlockState(standPos.below(2)));
        if (heat == null) {
            return new Status(Piece.NO_HEAT, waterLevel, LayeredCauldronBlock.MAX_FILL_LEVEL, null);
        }
        return new Status(Piece.RIGGED, waterLevel, LayeredCauldronBlock.MAX_FILL_LEVEL, heat);
    }

    /** True when a rig is fully formed — the tick loop's cheap yes/no. */
    public static boolean isRigged(BlockGetter level, BlockPos standPos) {
        return detect(level, standPos).rigged();
    }

    private static boolean isCauldron(BlockState state) {
        return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)
                || state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON);
    }

    @Nullable
    private static Heat heatSource(BlockState state) {
        if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
            return Heat.CAMPFIRE;
        }
        if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
            return Heat.SOUL_CAMPFIRE;
        }
        if (state.is(Blocks.FIRE)) {
            return Heat.FIRE;
        }
        if (state.is(Blocks.SOUL_FIRE)) {
            return Heat.SOUL_FIRE;
        }
        if (state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
            return Heat.LAVA;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return Heat.MAGMA;
        }
        return null;
    }
}
