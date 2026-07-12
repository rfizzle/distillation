package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.batch.BatchStand;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.compat.common.BrewingStandProbeTooltip;
import com.rfizzle.distillation.compat.common.CauldronRigProbeTooltip;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

/**
 * The Jade/WTHIT probe cores on a live server ({@code design/SPEC.md} §Compatibility): the shared
 * writer packs a rigged stand's state and its owner, the formatter renders them, and the cauldron
 * core reads the rig off synced block states. Both cores take resolved game objects, so they are
 * driven directly — the thin viewer adapters add nothing to test.
 */
public class ProbeTooltipGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 3, 1);
    private static final BlockPos CAULDRON = STAND.below();
    private static final BlockPos HEAT = STAND.below(2);

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void standProbeShowsProgressRigAndOwner(GameTestHelper helper) {
        BrewingStandBlockEntity stand = riggedStand(helper);
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            ((BatchStand) stand).distillation$setBrewTime(200); // halfway through a 400-tick cycle
            BatchStates.setOwner(stand, owner.getUUID());

            List<Component> lines = writeAndFormat(helper, stand);
            helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.brewing"), "shows brew progress while brewing");
            helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.rigged"), "shows the rig water level");
            helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.heat"), "shows the heat source");
            helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.owner"), "shows the batch owner");
        } finally {
            owner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void idleUnriggedStandProbeIsSilent(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        List<Component> lines = writeAndFormat(helper, stand);
        helper.assertTrue(lines.isEmpty(), "a bare idle stand has no Distillation probe line");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cauldronProbeShowsTheRigLine(GameTestHelper helper) {
        riggedStand(helper);
        List<Component> lines = CauldronRigProbeTooltip.buildLines(helper.getLevel(), helper.absolutePos(CAULDRON));
        helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.cauldron_rig"), "shows the batch-rig line");
        helper.assertTrue(hasKey(lines, "tooltip.distillation.probe.heat"), "shows the heat state");

        // A cauldron with no stand above it says nothing.
        helper.setBlock(STAND, Blocks.AIR.defaultBlockState());
        helper.assertTrue(CauldronRigProbeTooltip.buildLines(helper.getLevel(), helper.absolutePos(CAULDRON)).isEmpty(),
                "no stand above: no rig line");
        helper.succeed();
    }

    /** Own batch: flips the live batch-brewing toggle, so it must not overlap tests under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationProbeBatchOff")
    public void probesAreSilentWhenBatchBrewingDisabled(GameTestHelper helper) {
        boolean saved = com.rfizzle.distillation.Distillation.getConfig().enableBatchBrewing;
        com.rfizzle.distillation.Distillation.getConfig().enableBatchBrewing = false;
        try {
            BrewingStandBlockEntity stand = riggedStand(helper);
            // The rig physically exists, but the feature is off: no rig/owner line on the stand...
            helper.assertTrue(writeAndFormat(helper, stand).isEmpty(),
                    "an idle rigged stand shows nothing while batch brewing is disabled");
            // ...and no batch-rig line on the cauldron.
            helper.assertTrue(
                    CauldronRigProbeTooltip.buildLines(helper.getLevel(), helper.absolutePos(CAULDRON)).isEmpty(),
                    "the cauldron rig line is gated on the batch-brewing toggle");
        } finally {
            com.rfizzle.distillation.Distillation.getConfig().enableBatchBrewing = saved;
        }
        helper.succeed();
    }

    private static List<Component> writeAndFormat(GameTestHelper helper, BrewingStandBlockEntity stand) {
        CompoundTag tag = new CompoundTag();
        BrewingStandProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(STAND), stand);
        return BrewingStandProbeTooltip.buildLines(tag);
    }

    private static BrewingStandBlockEntity riggedStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());
        return helper.getBlockEntity(STAND);
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key));
    }
}
