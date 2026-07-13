package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchStand;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import com.rfizzle.distillation.redstone.ComparatorSignal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The brew-state comparator signal end to end ({@code design/SPEC.md} §9) at a real stand: the empty
 * zero, the working band climbing to the done band across a normal cycle, the rigged batch reaching
 * the six/thirteen ceiling, and the toggle-off fall-through to vanilla's container-fullness signal.
 * Exercises the block mixin, the block-entity reads, and the batch tick together.
 */
public class ComparatorGameTest implements FabricGameTest {

    private static final String BATCH = "distillationComparator";
    private static final BlockPos STAND = new BlockPos(1, 3, 1);
    private static final BlockPos CAULDRON = STAND.below();
    private static final BlockPos HEAT = STAND.below(2);
    /** A comparator east of the stand, reading it from the west, on its own stone support. */
    private static final BlockPos COMPARATOR = STAND.east();
    private static final BlockPos COMPARATOR_SUPPORT = COMPARATOR.below();
    /** Fuel loads and the 400-tick cycle starts on the first tick; margin past completion. */
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 520;
    /** A few ticks in, the cycle is running — enough to read the working band. */
    private static final int MID_CYCLE = 5;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void emptyStandReadsZero(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.assertTrue(signal(helper) == 0, "an empty stand reads 0, got " + signal(helper));
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = TIMEOUT)
    public void normalBrewClimbsFromWorkingToDone(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        for (int slot : new int[]{0, 1, 2}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(3, new ItemStack(Items.REDSTONE));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));

        helper.runAfterDelay(MID_CYCLE, () -> {
            helper.assertTrue(((BatchStand) stand).distillation$brewTime() > 0, "the cycle should be running");
            // Working band: three bottles mid-cycle read as their count.
            helper.assertTrue(signal(helper) == 3, "brewing three bottles reads 3, got " + signal(helper));
        });
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(((BatchStand) stand).distillation$brewTime() == 0, "the cycle should be finished");
            // Done band: idle with three potions reads count + 7.
            helper.assertTrue(signal(helper) == 10, "three finished bottles read 10, got " + signal(helper));
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = TIMEOUT)
    public void riggedBatchReachesTheSixAndThirteenCeiling(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BrewingStandBlockEntity stand = riggedStand(helper);
        DiscoveryManager.discoverAll(owner, RecipeGraphs.forLevel(helper.getLevel()));
        for (int slot : new int[]{0, 1, 2, 5, 6, 7}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(3, new ItemStack(Items.REDSTONE, 3));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BatchStates.setOwner(stand, owner.getUUID());

        helper.runAfterDelay(MID_CYCLE, () ->
                // Working band tops out at six: the full rigged batch counts its hidden row too.
                helper.assertTrue(signal(helper) == ComparatorSignal.WORKING_MAX,
                        "a rigged batch of six reads 6 mid-cycle, got " + signal(helper)));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(signal(helper) == 13, "six finished batch bottles read 13, got " + signal(helper));
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationComparatorDisabled")
    public void disabledRestoresVanillaFullness(GameTestHelper helper) {
        // Vanilla-parity guarantee: with the feature off, the stand keeps vanilla's exact
        // container-fullness signal — not zero, and not the brew-state band.
        boolean saved = Distillation.getConfig().enableComparatorOutput;
        Distillation.getConfig().enableComparatorOutput = false;
        try {
            helper.setBlock(STAND, Blocks.BREWING_STAND);
            BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
            for (int slot : new int[]{0, 1, 2}) {
                stand.setItem(slot, bottle(Potions.SWIFTNESS));
            }
            int vanilla = AbstractContainerMenu.getRedstoneSignalFromBlockEntity(stand);
            helper.assertTrue(signal(helper) == vanilla,
                    "disabled must fall through to vanilla fullness " + vanilla + ", got " + signal(helper));
            helper.assertTrue(vanilla != ComparatorSignal.of(false, 3),
                    "fullness must differ from the done band, or this test proves nothing");
        } finally {
            Distillation.getConfig().enableComparatorOutput = saved;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 80)
    public void drainingARiggedRowRepaintsAnAdjacentComparator(GameTestHelper helper) {
        // A rigged, idle stand holding finished bottles in the hidden batch row: draining the cauldron
        // un-rigs it (the row hides, is not ejected), and the brew-state signal drops the batch row.
        // A comparator caches its output, so it must be nudged to repaint — the cauldron change alone
        // never touches it. Assert the comparator's stored output actually falls, not just the on-read
        // signal (which recomputes fresh regardless).
        BrewingStandBlockEntity stand = riggedStand(helper);
        for (int slot : new int[]{5, 6, 7}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        // Place the comparator only once the stand has settled rigged. It faces WEST — a diode reads
        // its input from the block in its facing direction, i.e. the stand to the west — and is primed
        // with one setChanged so it latches the rigged done band before the drain.
        helper.runAfterDelay(MID_CYCLE, () -> {
            helper.setBlock(COMPARATOR_SUPPORT, Blocks.STONE);
            helper.setBlock(COMPARATOR,
                    Blocks.COMPARATOR.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            stand.setChanged();
            helper.runAfterDelay(MID_CYCLE, () -> {
                helper.assertTrue(comparatorOutput(helper) == 10,
                        "the comparator should read the done band; cmp=" + comparatorOutput(helper)
                                + " direct=" + signal(helper)
                                + " rigged=" + ((BatchStand) stand).distillation$isRigged());
                // Draining the cauldron (below the stand) touches no neighbour of the comparator, so
                // only the stand's own un-rig nudge can repaint it — this is what the fix adds.
                helper.setBlock(CAULDRON, Blocks.CAULDRON);
                helper.runAfterDelay(MID_CYCLE, () -> {
                    helper.assertTrue(!stand.getItem(5).isEmpty(), "a dry-out hides the batch row, never ejects it");
                    helper.assertTrue(comparatorOutput(helper) == 0,
                            "the comparator must repaint to 0 once the row hides; cmp=" + comparatorOutput(helper)
                                    + " direct=" + signal(helper)
                                    + " rigged=" + ((BatchStand) stand).distillation$isRigged());
                    helper.succeed();
                });
            });
        });
    }

    // --- helpers ---

    /** The comparator's stored output — stale unless the stand nudged it to repaint. */
    private static int comparatorOutput(GameTestHelper helper) {
        return ((ComparatorBlockEntity) helper.getBlockEntity(COMPARATOR)).getOutputSignal();
    }

    private static int signal(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(STAND);
        return helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs);
    }

    private static BlockState waterCauldron(int level) {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    private static BrewingStandBlockEntity riggedStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(CAULDRON, waterCauldron(2));
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());
        return helper.getBlockEntity(STAND);
    }

    private static ItemStack bottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }
}
