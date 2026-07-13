package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.advancement.DistillationAdvancements;
import com.rfizzle.distillation.api.DistillationBrewCallback;
import com.rfizzle.distillation.api.DistillationDiscoveryCallback;
import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The §10 advancement grants on a live server. Each milestone is driven through the same seam the game
 * uses — extraction for the "brew and obtain" ones, the discovery and brew callbacks for the derived
 * ones, a real antidote drink for Surgical — and asserted granted. Isolation cases pin the
 * automation-safety and threshold contracts (a bystander earns nothing; a partial set stays ungranted).
 */
public class AdvancementGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bottlingAMurkyDraughtGrantsTrialAndError(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper);
        ServerPlayer player = listeningPlayer(helper);
        ServerPlayer bystander = listeningPlayer(helper);
        try {
            stand.setItem(0, new ItemStack(DistillationItems.MURKY_DRAUGHT));
            takeBottleSlot(player, stand, 0);
            assertGranted(helper, player, "trial_and_error");
            assertNotGranted(helper, bystander, "trial_and_error"); // a bystander earns nothing
        } finally {
            player.discard();
            bystander.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void takingAPremiumPotionGrantsTheGoodStuff(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper);
        ServerPlayer player = listeningPlayer(helper);
        try {
            stand.setItem(0, PotionContents.createItemStack(Items.POTION, PremiumBrews.potion("premium_strength")));
            takeBottleSlot(player, stand, 0);
            assertGranted(helper, player, "the_good_stuff");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void brewingAllSevenMissingLinesGrantsTheMissingShelf(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper);
        ServerPlayer player = listeningPlayer(helper);
        try {
            String[] lines = {"resistance", "haste", "absorption", "luck", "glowing", "levitation", "health_boost"};
            for (int i = 0; i < lines.length; i++) {
                stand.setItem(0, PotionContents.createItemStack(Items.POTION, DistillationPotions.potion(lines[i])));
                takeBottleSlot(player, stand, 0);
                if (i < lines.length - 1) {
                    assertNotGranted(helper, player, "the_missing_shelf"); // partial set stays ungranted
                }
            }
            assertGranted(helper, player, "the_missing_shelf");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tenDiscoveriesGrantScholarButNotEveryDrop(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            List<ResourceLocation> ids = new ArrayList<>(graph.ids());
            helper.assertTrue(ids.size() > 10, "the live graph must hold more than ten conversions");
            for (int i = 0; i < 10; i++) {
                DiscoveryManager.record(player, ids.get(i));
            }
            // The discovery callback is the observer's seam — fire it as a real extraction would.
            DistillationDiscoveryCallback.EVENT.invoker().onDiscover(player, ids.get(9));
            assertGranted(helper, player, "scholar_of_the_still");
            assertNotGranted(helper, player, "every_drop");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoveringTheWholeGraphGrantsEveryDrop(GameTestHelper helper) {
        ServerPlayer player = listeningPlayer(helper);
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            DiscoveryManager.discoverAll(player, graph);
            DistillationDiscoveryCallback.EVENT.invoker().onDiscover(player, graph.ids().iterator().next());
            assertGranted(helper, player, "every_drop");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void completingABatchPassGrantsRoundForTheTableForTheOwner(GameTestHelper helper) {
        ServerPlayer owner = listeningPlayer(helper);
        try {
            ItemStack ingredient = new ItemStack(Items.NETHER_WART);
            // A normal (non-batch) pass grants nobody; a batch pass with no owner grants nobody.
            DistillationBrewCallback.EVENT.invoker().onBrew(
                    helper.getLevel(), STAND, ingredient, List.of(), owner.getUUID(), false);
            assertNotGranted(helper, owner, "round_for_the_table");
            DistillationBrewCallback.EVENT.invoker().onBrew(
                    helper.getLevel(), STAND, ingredient, List.of(), null, true);
            assertNotGranted(helper, owner, "round_for_the_table");

            DistillationBrewCallback.EVENT.invoker().onBrew(
                    helper.getLevel(), STAND, ingredient, List.of(), owner.getUUID(), true);
            assertGranted(helper, owner, "round_for_the_table");
        } finally {
            owner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void antidoteStrippingWithTwoEffectsLeftGrantsSurgical(GameTestHelper helper) {
        ServerPlayer keeper = listeningPlayer(helper);
        ServerPlayer lonely = listeningPlayer(helper);
        try {
            keeper.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            keeper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
            keeper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
            drink(helper, keeper, "poison_antidote");
            assertGranted(helper, keeper, "surgical");

            // Only one other effect remaining is not surgical.
            lonely.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            lonely.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
            drink(helper, lonely, "poison_antidote");
            assertNotGranted(helper, lonely, "surgical");
        } finally {
            keeper.discard();
            lonely.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fakePlayerAntidoteEarnsNoSurgical(GameTestHelper helper) {
        // A dispenser-thrown cure landing on automation must grant nothing (the fake-player guard).
        net.fabricmc.fabric.api.entity.FakePlayer fake =
                net.fabricmc.fabric.api.entity.FakePlayer.get(helper.getLevel());
        fake.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        fake.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        fake.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
        fake.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
        drink(helper, fake, "poison_antidote");
        helper.assertTrue(!fake.hasEffect(MobEffects.POISON), "the cure still strips the effect on a fake player");
        assertNotGranted(helper, fake, "surgical");
        helper.succeed();
    }

    // --- helpers ---

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        return helper.getBlockEntity(STAND);
    }

    /** A connected mock player whose advancement listeners are live before the first trigger fires. */
    private static ServerPlayer listeningPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static ItemStack takeBottleSlot(ServerPlayer player, BrewingStandBlockEntity stand, int slot) {
        BrewingStandMenu menu = new BrewingStandMenu(1, player.getInventory(), stand, new SimpleContainerData(2));
        return menu.slots.get(slot).safeTake(64, Integer.MAX_VALUE, player);
    }

    /** Drinks the named antidote through the real finish-using path, applying its instant cleanse. */
    private static void drink(GameTestHelper helper, ServerPlayer player, String antidotePath) {
        ItemStack antidote = PotionContents.createItemStack(Items.POTION,
                BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, Distillation.id(antidotePath)))
                        .orElseThrow());
        antidote.getItem().finishUsingItem(antidote, helper.getLevel(), player);
    }

    private static void assertGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Distillation.id(path));
        helper.assertTrue(holder != null, "advancement " + path + " should be loaded (JSON present)");
        helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(),
                "advancement " + path + " should be granted");
    }

    private static void assertNotGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Distillation.id(path));
        helper.assertTrue(holder != null, "advancement " + path + " should be loaded (JSON present)");
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(),
                "advancement " + path + " should not be granted yet");
    }
}
