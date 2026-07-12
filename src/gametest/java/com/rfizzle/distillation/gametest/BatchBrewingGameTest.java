package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchStand;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.batch.BatchRig;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * The batch rig end to end ({@code design/SPEC.md} §3) at a real stand: rig detection, an engaged
 * six-bottle pass and its 3-ingredient / 2-fuel / 1-water cost, the fall-back to a normal pass, the
 * discovery gate, hopper lock-out, ownership, and the eject on un-rig. Exercises the block-entity
 * mixin, the menu container growth, the brew seam, and the batch logic together.
 *
 * <p>All tests live in one batch and brew a <em>vanilla</em> conversion (redstone extends
 * swiftness), so they stay immune to the {@code enableMissingBrews} kill switch a sibling batch
 * flips. The finicky-to-place heat sources (fire, soul fire, lava) are covered over real block
 * states by {@code BatchRigTest}; this suite places the stable ones.
 */
public class BatchBrewingGameTest implements FabricGameTest {

    private static final String BATCH = "distillationBatch";
    private static final BlockPos STAND = new BlockPos(1, 3, 1);
    private static final BlockPos CAULDRON = STAND.below();
    private static final BlockPos HEAT = STAND.below(2);
    /** Fuel loads on the first tick, the 400-tick cycle starts the same tick; margin for safety. */
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 520;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void rigDetectsStableHeatSources(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(CAULDRON, waterCauldron(2));
        Object[][] cases = {
                {Blocks.CAMPFIRE.defaultBlockState(), BatchRig.Heat.CAMPFIRE},
                {Blocks.SOUL_CAMPFIRE.defaultBlockState(), BatchRig.Heat.SOUL_CAMPFIRE},
                {Blocks.MAGMA_BLOCK.defaultBlockState(), BatchRig.Heat.MAGMA},
        };
        for (Object[] heatCase : cases) {
            helper.setBlock(HEAT, (BlockState) heatCase[0]);
            BatchRig.Status status = BatchRig.detect(helper.getLevel(), helper.absolutePos(STAND));
            helper.assertTrue(status.rigged() && status.heat() == heatCase[1],
                    "expected rig lit by " + heatCase[1] + " but got " + status.piece() + "/" + status.heat());
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void rigReportsTheFirstMissingPiece(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());

        helper.setBlock(CAULDRON, Blocks.AIR.defaultBlockState());
        helper.assertTrue(detect(helper).piece() == BatchRig.Piece.NO_CAULDRON, "no cauldron below");

        helper.setBlock(CAULDRON, Blocks.CAULDRON.defaultBlockState());
        helper.assertTrue(detect(helper).piece() == BatchRig.Piece.NO_WATER, "empty cauldron holds no water");

        helper.setBlock(CAULDRON, waterCauldron(2));
        helper.setBlock(HEAT, Blocks.AIR.defaultBlockState());
        helper.assertTrue(detect(helper).piece() == BatchRig.Piece.NO_HEAT, "no heat below the cauldron");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = TIMEOUT)
    public void engagedPassFillsSixAndConsumesTheRigCost(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BrewingStandBlockEntity stand = riggedStand(helper);
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        // A discovery-disabled sibling batch cannot break this: with discovery off, every conversion
        // counts as discovered, so the pass engages either way (SPEC §3).
        DiscoveryManager.discoverAll(owner, graph);
        String expected = extendedSwiftnessId(graph);

        for (int slot : new int[]{0, 1, 2, 5, 6, 7}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(3, new ItemStack(Items.REDSTONE, 3));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BatchStates.setOwner(stand, owner.getUUID());

        helper.runAfterDelay(BREW_WAIT, () -> {
            for (int slot : new int[]{0, 1, 2, 5, 6, 7}) {
                assertPotion(helper, stand.getItem(slot), expected);
            }
            helper.assertTrue(stand.getItem(3).isEmpty(), "a batch pass consumes 3 ingredients");
            helper.assertTrue(((BatchStand) stand).distillation$fuel() == 18, "a batch pass consumes 2 fuel");
            BlockState cauldron = helper.getLevel().getBlockState(helper.absolutePos(CAULDRON));
            helper.assertTrue(cauldron.is(Blocks.WATER_CAULDRON)
                            && cauldron.getValue(LayeredCauldronBlock.LEVEL) == 1,
                    "a batch pass consumes 1 water level");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = TIMEOUT)
    public void anEmptyBatchRowFallsBackToANormalPass(GameTestHelper helper) {
        BrewingStandBlockEntity stand = riggedStand(helper);
        String expected = extendedSwiftnessId(RecipeGraphs.forLevel(helper.getLevel()));
        for (int slot : new int[]{0, 1, 2}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(3, new ItemStack(Items.REDSTONE, 3));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));

        helper.runAfterDelay(BREW_WAIT, () -> {
            for (int slot : new int[]{0, 1, 2}) {
                assertPotion(helper, stand.getItem(slot), expected);
            }
            // Exactly one ingredient consumed is the definitive normal-vs-batch signal: a batch pass
            // would have consumed batchIngredientCost (3), emptying the slot. (Fuel is not asserted
            // here — the brewed bottles start a follow-up murky pass that consumes a second charge;
            // the 2-fuel batch cost is pinned by engagedPassFillsSixAndConsumesTheRigCost.)
            helper.assertTrue(stand.getItem(3).getCount() == 2,
                    "an empty batch row costs one ingredient, not three (left=" + stand.getItem(3).getCount() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = TIMEOUT)
    public void undiscoveredBatchBottlesAreSkippedUntouched(GameTestHelper helper) {
        // The per-bottle discovery gate is what this asserts, so pin discovery on for the test's run.
        boolean savedDiscovery = Distillation.getConfig().enableDiscovery;
        Distillation.getConfig().enableDiscovery = true;

        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BrewingStandBlockEntity stand = riggedStand(helper);
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        ItemStack redstone = new ItemStack(Items.REDSTONE, 3);
        RecipeGraph.Conversion swiftConversion = graph.matchConversion(redstone, bottle(Potions.SWIFTNESS));
        helper.assertTrue(swiftConversion != null && graph.matchConversion(redstone, bottle(Potions.STRENGTH)) != null,
                "redstone must extend both swiftness and strength for this test");

        // The owner has learned only the swiftness conversion, so the strength bottle beside it is skipped.
        DiscoveryManager.record(owner, swiftConversion.id());
        stand.setItem(5, bottle(Potions.SWIFTNESS));
        stand.setItem(6, bottle(Potions.STRENGTH));
        stand.setItem(3, redstone);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BatchStates.setOwner(stand, owner.getUUID());

        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(!isPotion(stand.getItem(5), "minecraft:swiftness"),
                    "a discovered batch bottle brews onward");
            assertPotion(helper, stand.getItem(6), "minecraft:strength");
            Distillation.getConfig().enableDiscovery = savedDiscovery;
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void hoppersCannotReachTheBatchSlots(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        for (Direction direction : Direction.values()) {
            for (int slot : stand.getSlotsForFace(direction)) {
                helper.assertTrue(slot < 5, "no face may expose a batch slot, but " + direction + " exposed " + slot);
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void aDirectIngredientInsertDisownsTheStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        BatchStates.setOwner(stand, UUID.randomUUID());
        helper.assertTrue(BatchStates.owner(stand).isPresent(), "owner should be set");

        // A hopper insert routes through setItem with no click context, clearing the owner.
        stand.setItem(3, new ItemStack(Items.REDSTONE));
        helper.assertTrue(BatchStates.owner(stand).isEmpty(), "a hopper insert must disown the stand");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
    public void breakingTheRigEjectsTheBatchRow(GameTestHelper helper) {
        BrewingStandBlockEntity stand = riggedStand(helper);
        for (int slot : new int[]{5, 6, 7}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        helper.runAfterDelay(3, () -> {
            helper.setBlock(CAULDRON, Blocks.AIR.defaultBlockState()); // break the rig
            helper.runAfterDelay(3, () -> {
                for (int slot : new int[]{5, 6, 7}) {
                    helper.assertTrue(stand.getItem(slot).isEmpty(),
                            "breaking the rig must eject batch slot " + slot);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void rigCommandIsOpenToEveryone(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("distillation");
        helper.assertTrue(root != null && root.getChild("rig") != null, "/distillation rig must be registered");
        var nonOp = helper.getLevel().getServer().createCommandSourceStack().withPermission(0);
        helper.assertTrue(root.getChild("rig").canUse(nonOp), "/distillation rig must be open to everyone");
        helper.succeed();
    }

    // --- helpers ---

    private static BlockState waterCauldron(int level) {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    /** A stand over a water cauldron (level 2, so a consume is visible) heated by a lit campfire. */
    private static BrewingStandBlockEntity riggedStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(CAULDRON, waterCauldron(2));
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());
        return helper.getBlockEntity(STAND);
    }

    private static BatchRig.Status detect(GameTestHelper helper) {
        return BatchRig.detect(helper.getLevel(), helper.absolutePos(STAND));
    }

    /** The output id of the vanilla swiftness+redstone conversion, read from the live graph. */
    private static String extendedSwiftnessId(RecipeGraph graph) {
        RecipeGraph.Conversion conversion =
                graph.matchConversion(new ItemStack(Items.REDSTONE), bottle(Potions.SWIFTNESS));
        return potionId(graph.outputOf(conversion, bottle(Potions.SWIFTNESS)));
    }

    private static ItemStack bottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static String potionId(ItemStack stack) {
        ResourceLocation id = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion().flatMap(Holder::unwrapKey).map(key -> key.location()).orElse(null);
        return id == null ? null : id.toString();
    }

    private static boolean isPotion(ItemStack stack, String potionId) {
        return potionId.equals(potionId(stack));
    }

    private static void assertPotion(GameTestHelper helper, ItemStack stack, String expectedPotionId) {
        helper.assertTrue(isPotion(stack, expectedPotionId),
                "expected bottle to hold " + expectedPotionId + " but found " + stack);
    }
}
