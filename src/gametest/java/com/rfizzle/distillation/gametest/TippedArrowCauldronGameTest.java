package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.arrow.PotionCauldrons;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The potion-cauldron dip end to end ({@code design/SPEC.md} §Tipped arrows): a discovered drinkable
 * potion charges a water cauldron, arrows dipped into it tip at the configured rate and spend one
 * water level per dip, draining the last level clears the charge, the discovery gate blocks an
 * unlearned brew, splash potions get no handler (vanilla's lingering recipe stands), and the
 * feature toggled off is inert. Drives the registered {@code CauldronInteraction.WATER} handlers
 * directly against a real cauldron and a connected mock player.
 */
public class TippedArrowCauldronGameTest implements FabricGameTest {

    private static final BlockPos CAULDRON = new BlockPos(1, 2, 1);
    private static final Holder<Potion> BREW = Potions.SWIFTNESS; // vanilla, always in the graph

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoveredPotionChargesCauldron(GameTestHelper helper) {
        pinFeature(true);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON);
        helper.setBlock(CAULDRON, waterCauldron(3));
        ServerPlayer player = discoveredPlayer(helper);

        charge(helper, player, pos, BREW);

        helper.assertTrue(PotionCauldrons.chargedPotion(level, pos).isPresent(), "cauldron is charged");
        helper.assertTrue(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL) == 3,
                "charging keeps the water level");
        helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "the emptied potion returns a glass bottle");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void arrowsDipIntoAChargedCauldron(GameTestHelper helper) {
        pinFeature(true);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON);
        helper.setBlock(CAULDRON, waterCauldron(3));
        ServerPlayer player = discoveredPlayer(helper);
        charge(helper, player, pos, BREW);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 64));
        dip(helper, player, pos);

        helper.assertTrue(tippedArrowCount(player, BREW) == 8, "a dip tips the default eight arrows");
        helper.assertTrue(player.getMainHandItem().getCount() == 56, "eight arrows are consumed");
        helper.assertTrue(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL) == 2,
                "a dip spends one water level");
        helper.assertTrue(PotionCauldrons.chargedPotion(level, pos).isPresent(), "levels remain, so the charge holds");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drainingTheLastLevelClearsTheCharge(GameTestHelper helper) {
        pinFeature(true);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON);
        helper.setBlock(CAULDRON, waterCauldron(1));
        ServerPlayer player = discoveredPlayer(helper);
        charge(helper, player, pos, BREW);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 64));
        dip(helper, player, pos);

        helper.assertBlockPresent(Blocks.CAULDRON, CAULDRON); // level 1 -> 0 empties to a plain cauldron
        helper.assertTrue(PotionCauldrons.chargedPotion(level, pos).isEmpty(), "an emptied cauldron drops its charge");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void undiscoveredPotionDoesNotCharge(GameTestHelper helper) {
        pinFeature(true);
        Distillation.getConfig().enableDiscovery = true; // the gate under test
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CAULDRON);
        helper.setBlock(CAULDRON, waterCauldron(3));
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper); // learns nothing

        charge(helper, player, pos, BREW);

        helper.assertTrue(PotionCauldrons.chargedPotion(level, pos).isEmpty(), "an unlearned brew never charges");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void splashPotionsHaveNoHandler(GameTestHelper helper) {
        // Splash/lingering items are never registered, so vanilla's lingering-arrow recipe is untouched.
        helper.assertFalse(CauldronInteraction.WATER.map().containsKey(Items.SPLASH_POTION),
                "splash potions must not gain a cauldron handler");
        helper.assertFalse(CauldronInteraction.WATER.map().containsKey(Items.LINGERING_POTION),
                "lingering potions must not gain a cauldron handler");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void featureOffIsInert(GameTestHelper helper) {
        pinFeature(false);
        try {
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(CAULDRON);
            helper.setBlock(CAULDRON, waterCauldron(3));
            ServerPlayer player = discoveredPlayer(helper);

            charge(helper, player, pos, BREW);

            helper.assertTrue(PotionCauldrons.chargedPotion(level, pos).isEmpty(),
                    "with the feature off a potion never charges the cauldron");
            helper.assertTrue(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "a non-water potion still no-ops against the cauldron, exactly as vanilla");
        } finally {
            pinFeature(true); // restore for sibling tests sharing the config singleton
        }
        helper.succeed();
    }

    // --- helpers ---

    private static void pinFeature(boolean enabled) {
        Distillation.getConfig().enableTippedArrows = enabled;
    }

    private static BlockState waterCauldron(int level) {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    /** A connected survival player who has discovered a conversion producing {@link #BREW}. */
    private static ServerPlayer discoveredPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL); // instabuild off, so potions and arrows are actually consumed
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        RecipeGraph.PotionConversion producing = graph.conversionsProducing(BREW).get(0);
        DiscoveryManager.record(player, producing.id());
        return player;
    }

    private static void charge(GameTestHelper helper, ServerPlayer player, BlockPos pos, Holder<Potion> potion) {
        player.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, potion));
        interact(helper, player, pos, Items.POTION);
    }

    private static void dip(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        interact(helper, player, pos, Items.ARROW);
    }

    private static void interact(GameTestHelper helper, ServerPlayer player, BlockPos pos, net.minecraft.world.item.Item item) {
        ServerLevel level = helper.getLevel();
        CauldronInteraction handler = CauldronInteraction.WATER.map().get(item);
        handler.interact(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND,
                player.getMainHandItem());
    }

    private static int tippedArrowCount(ServerPlayer player, Holder<Potion> potion) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.TIPPED_ARROW)
                    && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
