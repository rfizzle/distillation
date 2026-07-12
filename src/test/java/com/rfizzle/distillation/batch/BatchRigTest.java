// Tier: 2 (fabric-loader-junit + Bootstrap — detection reads real BlockStates)
package com.rfizzle.distillation.batch;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rig detection ({@code design/SPEC.md} §3): a water cauldron with ≥1 level directly below the
 * stand and one of the six heat sources directly below the cauldron. Covers every heat source and
 * each way the rig fails, reported as the first missing piece top-down.
 */
class BatchRigTest {

    private static final BlockPos STAND = new BlockPos(0, 10, 0);
    private static final BlockPos CAULDRON = STAND.below();
    private static final BlockPos HEAT = STAND.below(2);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyHeatSourceLightsTheRig() {
        assertRigged(waterCauldron(2), Blocks.CAMPFIRE.defaultBlockState(), 2, BatchRig.Heat.CAMPFIRE);
        assertRigged(waterCauldron(3), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3, BatchRig.Heat.SOUL_CAMPFIRE);
        assertRigged(waterCauldron(1), Blocks.FIRE.defaultBlockState(), 1, BatchRig.Heat.FIRE);
        assertRigged(waterCauldron(1), Blocks.SOUL_FIRE.defaultBlockState(), 1, BatchRig.Heat.SOUL_FIRE);
        assertRigged(waterCauldron(2), Blocks.LAVA.defaultBlockState(), 2, BatchRig.Heat.LAVA);
        assertRigged(waterCauldron(2), Blocks.MAGMA_BLOCK.defaultBlockState(), 2, BatchRig.Heat.MAGMA);
    }

    @Test
    void missingCauldronIsReportedFirst() {
        assertPiece(Blocks.AIR.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), BatchRig.Piece.NO_CAULDRON);
    }

    @Test
    void anEmptyOrNonWaterCauldronHoldsNoWater() {
        assertPiece(Blocks.CAULDRON.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), BatchRig.Piece.NO_WATER);
        assertPiece(Blocks.LAVA_CAULDRON.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(),
                BatchRig.Piece.NO_WATER);
    }

    @Test
    void aWaterCauldronWithNoHeatIsNotRigged() {
        assertPiece(waterCauldron(2), Blocks.AIR.defaultBlockState(), BatchRig.Piece.NO_HEAT);
    }

    @Test
    void anUnlitCampfireIsNotHeat() {
        assertPiece(waterCauldron(2), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
                BatchRig.Piece.NO_HEAT);
    }

    private static BlockState waterCauldron(int level) {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    private static void assertRigged(BlockState cauldron, BlockState heat, int waterLevel, BatchRig.Heat expected) {
        BatchRig.Status status = detect(cauldron, heat);
        assertEquals(BatchRig.Piece.RIGGED, status.piece(), "should be rigged for heat " + expected);
        assertEquals(waterLevel, status.waterLevel());
        assertEquals(expected, status.heat());
    }

    private static void assertPiece(BlockState cauldron, BlockState heat, BatchRig.Piece expected) {
        assertEquals(expected, detect(cauldron, heat).piece());
    }

    private static BatchRig.Status detect(BlockState cauldron, BlockState heat) {
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(CAULDRON, cauldron);
        world.put(HEAT, heat);
        return BatchRig.detect(new FakeBlockGetter(world), STAND);
    }

    /** A minimal {@link BlockGetter} over a fixed block map — detection only reads block states. */
    private record FakeBlockGetter(Map<BlockPos, BlockState> states) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
