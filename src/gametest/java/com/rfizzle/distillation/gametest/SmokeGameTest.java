package com.rfizzle.distillation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Exercises the gametest wiring before any real feature depends on it: the
 * server boots with the mod loaded and a world edit round-trips.
 */
public class SmokeGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modLoadsAndWorldResponds(GameTestHelper helper) {
        helper.setBlock(net.minecraft.core.BlockPos.ZERO.above(), Blocks.BREWING_STAND);
        helper.assertBlockPresent(Blocks.BREWING_STAND, net.minecraft.core.BlockPos.ZERO.above());
        helper.succeed();
    }
}
