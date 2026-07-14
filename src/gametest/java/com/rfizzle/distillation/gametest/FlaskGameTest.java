package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.FlaskItem;
import com.rfizzle.distillation.item.FlaskPour;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flask end to end ({@code design/SPEC.md} §12) on a live server: drinking a dose full or as a
 * sneak-sipped half, an instant brew drinking whole, the off-hand pour with its discovery gate and
 * brew/full guards, filling a flask from a batch pass alongside its bottles, and the feature-off
 * parity. Exercises the flask item, the pour seam, the batch choke point, and the container gate.
 */
public class FlaskGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 3, 1);
    private static final BlockPos CAULDRON = STAND.below();
    private static final BlockPos HEAT = STAND.below(2);
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 520;
    private static final int TOLERANCE = 20;

    private static final int FIRE_RES_HONEST = 9600; // SPEC §4: 8:00
    private static final int FIRE_RES_HALF = 4800;   // ⌊9600 ÷ 2⌋

    // --- drinking ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drinkingADoseAppliesTheFullBrewAndSpendsIt(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack flask = flask("minecraft:fire_resistance", 6); // three doses
            flask.getItem().finishUsingItem(flask, helper.getLevel(), player);
            assertDuration(helper, player, MobEffects.FIRE_RESISTANCE, FIRE_RES_HONEST,
                    "a full dose applies the brew's full honest duration");
            helper.assertTrue(FlaskItem.doses(flask) == 4, "a full dose spends two half-units");
            helper.assertTrue(flask.is(DistillationItems.FLASK), "the flask is reusable — it stays in hand");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sneakSippingSpendsAHalfThenTheNextDrinkFinishesIt(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack flask = flask("minecraft:fire_resistance", 6);
            player.setShiftKeyDown(true);
            flask.getItem().finishUsingItem(flask, helper.getLevel(), player); // sip half a dose
            assertDuration(helper, player, MobEffects.FIRE_RESISTANCE, FIRE_RES_HALF,
                    "a sneak-sip applies half the dose");
            helper.assertTrue(FlaskItem.doses(flask) == 5, "a sip spends one half-unit, leaving a pending half");

            player.setShiftKeyDown(false);
            flask.getItem().finishUsingItem(flask, helper.getLevel(), player); // pending half finishes
            helper.assertTrue(FlaskItem.doses(flask) == 4,
                    "the pending half finishes on the next drink, sneaking or not");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSipLatchesAndSurvivesReleasingTheSneak(GameTestHelper helper) {
        // SPEC §12: the sip/full choice is latched when the drink starts (shared DraughtDrinker seam),
        // so releasing the sneak mid-drink flips neither the swallow speed nor the half-dose spent.
        ServerPlayer player = survivalPlayer(helper);
        player.setShiftKeyDown(true);
        try {
            ItemStack flask = flask("minecraft:fire_resistance", 6);
            player.setItemInHand(InteractionHand.MAIN_HAND, flask);
            player.startUsingItem(InteractionHand.MAIN_HAND); // latches SIP_HALF from the crouch at start

            player.setShiftKeyDown(false); // let go of the sneak before the drink finishes
            ItemStack using = player.getUseItem();
            helper.assertTrue(using.getItem().getUseDuration(using, player) == 16,
                    "a latched sip keeps the quick half-swallow time after the crouch is released");

            using.getItem().finishUsingItem(using, helper.getLevel(), player);
            assertDuration(helper, player, MobEffects.FIRE_RESISTANCE, FIRE_RES_HALF,
                    "a latched sip applies the half dose even though the crouch was released");
            helper.assertTrue(FlaskItem.doses(using) == 5,
                    "a latched sip spends only one half-unit, never a full dose");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anInstantBrewDrinksWholePerDoseEvenSneaking(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setShiftKeyDown(true);
        player.setHealth(4.0F);
        try {
            ItemStack flask = flask("minecraft:healing", 2); // one dose of an instant brew
            flask.getItem().finishUsingItem(flask, helper.getLevel(), player);
            helper.assertTrue(player.getHealth() > 4.0F, "an instant brew applies whole, not sipped");
            helper.assertTrue(FlaskItem.doses(flask) == 0, "the instant dose spends a whole dose (two halves)");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anEmptyFlaskDoesNothingOnUse(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack empty = new ItemStack(DistillationItems.FLASK);
            player.setItemInHand(InteractionHand.MAIN_HAND, empty);
            var result = empty.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult() == net.minecraft.world.InteractionResult.PASS,
                    "an empty flask passes its use — no drink starts");
            helper.assertTrue(!player.isUsingItem(), "an empty flask starts no drink");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // --- pour ---

    /** Own batch: flips enableDiscovery so any brew pours; must not overlap discovery-on tests. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationFlaskPour")
    public void pouringABottleFillsADoseAndReturnsTheGlass(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableDiscovery;
        Distillation.getConfig().enableDiscovery = false;
        ServerPlayer player = survivalPlayer(helper);
        try {
            // The off-hand-flask + main-hand-potion pour seam, through the potion's own use().
            ItemStack flask = new ItemStack(DistillationItems.FLASK);
            player.setItemInHand(InteractionHand.OFF_HAND, flask);
            ItemStack potion = potion("minecraft:fire_resistance");
            player.setItemInHand(InteractionHand.MAIN_HAND, potion);
            potion.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(FlaskItem.doses(player.getOffhandItem()) == 2, "a pour adds one dose");
            helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "a pour returns the glass bottle");

            // A full flask and a brew-mismatched flask both fall through (tryPour returns null).
            ItemStack full = flask("minecraft:fire_resistance", 6);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:fire_resistance"));
            helper.assertTrue(FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), full) == null, "a full flask does not pour");
            ItemStack partial = flask("minecraft:fire_resistance", 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:swiftness"));
            helper.assertTrue(FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), partial) == null, "a different brew does not pour");
            helper.assertTrue(FlaskItem.doses(partial) == 2, "a refused pour leaves the flask untouched");
        } finally {
            Distillation.getConfig().enableDiscovery = saved;
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void pouringRequiresDiscovery(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack flask = new ItemStack(DistillationItems.FLASK);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:fire_resistance"));
            FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), flask);
            helper.assertTrue(FlaskItem.doses(flask) == 0,
                    "an undiscovered brew must not pour with discovery on");

            // An unproducible base/foreign potion is not a pourable brew (SPEC §12): it falls through
            // to a normal drink, never a false discovery gate. minecraft:luck has no producing edge.
            ItemStack unproducible = new ItemStack(DistillationItems.FLASK);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:luck"));
            helper.assertTrue(FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), unproducible) == null,
                    "an unproducible potion falls through to a normal drink, not a false discovery gate");
            helper.assertTrue(FlaskItem.doses(unproducible) == 0, "and it does not pour");

            DiscoveryManager.discoverAll(player, RecipeGraphs.forLevel(helper.getLevel()));
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:fire_resistance"));
            FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), flask);
            helper.assertTrue(FlaskItem.doses(flask) == 2, "a discovered brew pours");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // --- batch fill ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationFlaskBatch", timeoutTicks = TIMEOUT)
    public void aBatchPassFillsAFlaskInTheRow(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BrewingStandBlockEntity stand = riggedStand(helper);
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        DiscoveryManager.discoverAll(owner, graph);
        String expected = extendedSwiftnessId(graph);

        for (int slot : new int[]{0, 1, 2, 5, 6}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(7, new ItemStack(DistillationItems.FLASK)); // an empty flask alongside the bottles
        stand.setItem(3, new ItemStack(Items.REDSTONE, 3));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BatchStates.setOwner(stand, owner.getUUID());

        helper.runAfterDelay(BREW_WAIT, () -> {
            ItemStack flask = stand.getItem(7);
            helper.assertTrue(flask.is(DistillationItems.FLASK) && FlaskItem.doses(flask) == 6,
                    "a flask in the batch row fills to three doses in one pass");
            helper.assertTrue(expected.equals(potionId(flask)),
                    "the flask fills with the pass's own brew");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationFlaskBatch", timeoutTicks = TIMEOUT)
    public void aBatchPassSkipsAMismatchedFlask(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BrewingStandBlockEntity stand = riggedStand(helper);
        DiscoveryManager.discoverAll(owner, RecipeGraphs.forLevel(helper.getLevel()));

        for (int slot : new int[]{0, 1, 2, 5, 6}) {
            stand.setItem(slot, bottle(Potions.SWIFTNESS));
        }
        stand.setItem(7, flask("minecraft:fire_resistance", 2)); // holds a different brew
        stand.setItem(3, new ItemStack(Items.REDSTONE, 3));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        BatchStates.setOwner(stand, owner.getUUID());

        helper.runAfterDelay(BREW_WAIT, () -> {
            ItemStack flask = stand.getItem(7);
            helper.assertTrue(FlaskItem.doses(flask) == 2 && "minecraft:fire_resistance".equals(potionId(flask)),
                    "a flask holding a different brew is left untouched, never overwritten or murked");
            helper.succeed();
        });
    }

    // --- feature off ---

    /** Own batch: flips enableFlask; must not overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationFlaskOff")
    public void flaskDisabledRefusesPourAndTheStandButExistingFlasksDrink(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableFlask;
        Distillation.getConfig().enableFlask = false;
        ServerPlayer player = survivalPlayer(helper);
        try {
            // No new pours.
            ItemStack empty = new ItemStack(DistillationItems.FLASK);
            player.setItemInHand(InteractionHand.MAIN_HAND, potion("minecraft:fire_resistance"));
            helper.assertTrue(FlaskPour.tryPour(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    player.getMainHandItem(), empty) == null, "the pour is refused with the feature off");
            helper.assertTrue(FlaskItem.doses(empty) == 0, "no dose is added with the feature off");

            // The stand refuses a flask in the batch row.
            helper.setBlock(STAND, Blocks.BREWING_STAND);
            BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
            helper.assertTrue(!stand.canPlaceItem(BatchBrew.FIRST_BATCH_SLOT, new ItemStack(DistillationItems.FLASK)),
                    "the batch row refuses a flask with the feature off");

            // An already-filled flask still drinks — production is gated, not consumption.
            ItemStack filled = flask("minecraft:fire_resistance", 4);
            filled.getItem().finishUsingItem(filled, helper.getLevel(), player);
            helper.assertTrue(FlaskItem.doses(filled) == 2, "an existing filled flask still drinks a dose");
        } finally {
            Distillation.getConfig().enableFlask = saved;
            player.discard();
        }
        helper.succeed();
    }

    // --- helpers ---

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static BrewingStandBlockEntity riggedStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());
        return helper.getBlockEntity(STAND);
    }

    private static PotionContents contentsOf(String potionId) {
        Holder<Potion> holder = BuiltInRegistries.POTION
                .getHolder(ResourceKey.create(Registries.POTION, ResourceLocation.parse(potionId)))
                .orElseThrow();
        return new PotionContents(holder);
    }

    private static ItemStack potion(String potionId) {
        return PotionContents.createItemStack(Items.POTION, contentsOf(potionId).potion().orElseThrow());
    }

    private static ItemStack bottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static ItemStack flask(String potionId, int halves) {
        ItemStack stack = new ItemStack(DistillationItems.FLASK);
        stack.set(DataComponents.POTION_CONTENTS, contentsOf(potionId));
        stack.set(DistillationItems.FLASK_DOSES, halves);
        return stack;
    }

    private static String potionId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion().flatMap(Holder::unwrapKey).map(key -> key.location().toString()).orElse(null);
    }

    private static String extendedSwiftnessId(RecipeGraph graph) {
        RecipeGraph.Conversion conversion =
                graph.matchConversion(new ItemStack(Items.REDSTONE), bottle(Potions.SWIFTNESS));
        return potionId(graph.outputOf(conversion, bottle(Potions.SWIFTNESS)));
    }

    private static void assertDuration(GameTestHelper helper, ServerPlayer player,
                                       Holder<net.minecraft.world.effect.MobEffect> effect, int expected, String message) {
        var instance = player.getEffect(effect);
        helper.assertTrue(instance != null, message + " (effect absent)");
        helper.assertTrue(Math.abs(instance.getDuration() - expected) <= TOLERANCE,
                message + " — expected ~" + expected + " but was " + instance.getDuration());
    }
}
