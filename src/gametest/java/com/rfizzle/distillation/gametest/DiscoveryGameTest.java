package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.discovery.DiscoveryData;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * The discovery contract of {@code design/SPEC.md} §1 on a live server: taking a brewed output
 * records exactly one discovery for the taking player; hoppers teach nobody; discovery survives
 * death; the kill switch and {@code startDiscovered} behave; and a foreign bottle can't ride an
 * old brew's provenance.
 */
public class DiscoveryGameTest implements FabricGameTest {

    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 600;
    private static final ResourceLocation WATER_TO_AWKWARD = ResourceLocation.parse("distillation:nether_wart/water");

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void takingBrewedOutputRecordsExactlyOneDiscovery(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeBrewingStand(helper, new BlockPos(1, 2, 1),
                PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART));

        helper.runAfterDelay(BREW_WAIT, () -> {
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                ItemStack taken = takeBottleSlot(player, stand, 0);
                helper.assertTrue(!taken.isEmpty(), "the brewed output must be takeable");

                DiscoveryData data = DiscoveryManager.data(player);
                helper.assertTrue(data.contains(WATER_TO_AWKWARD),
                        "taking the brewed output must record its conversion");
                helper.assertTrue(data.orderedIds().size() == 1,
                        "exactly one discovery is recorded per conversion");
                helper.assertTrue(!DiscoveryManager.record(player, WATER_TO_AWKWARD),
                        "re-discovery must be silently idempotent");
                helper.assertTrue(data.orderedIds().size() == 1, "re-discovery must not grow the set");
                // The take above walked the celebrate path (toast + chime to a live connection);
                // pin the chime's registration so a renamed id can't silently mute it.
                helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                                .containsKey(Distillation.id("ui.recipe_learned")),
                        "the discovery chime SoundEvent must be registered");
            } finally {
                player.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void hopperExtractionTeachesNobody(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 3, 1);
        BrewingStandBlockEntity stand = placeBrewingStand(helper, standPos,
                PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART));
        ServerPlayer bystander = MockPlayers.serverPlayerInLevel(helper);

        // The hopper goes in only after the brew completes — a hopper under the stand would pull
        // the water bottle out mid-cycle (bottle slots are extractable at any time).
        helper.runAfterDelay(BREW_WAIT, () ->
                helper.setBlock(standPos.below(), Blocks.HOPPER));

        helper.succeedWhen(() -> {
            HopperBlockEntity hopper = helper.getBlockEntity(standPos.below());
            boolean pulled = false;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                pulled |= hopper.getItem(slot).is(Items.POTION);
            }
            helper.assertTrue(pulled, "hopper has not pulled the brewed bottle yet");
            try {
                helper.assertTrue(DiscoveryManager.data(bystander).orderedIds().isEmpty(),
                        "hopper-extracted outputs must teach nobody");
            } finally {
                bystander.discard();
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoverySurvivesDeathRespawn(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer respawned = null;
        try {
            helper.assertTrue(DiscoveryManager.record(player, WATER_TO_AWKWARD),
                    "recording on a fresh player must report a new discovery");
            respawned = player.getServer().getPlayerList()
                    .respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(DiscoveryManager.data(respawned).contains(WATER_TO_AWKWARD),
                    "discovery must survive death (copy-on-death attachment)");
        } finally {
            player.discard();
            if (respawned != null) {
                respawned.discard();
            }
        }
        helper.succeed();
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationDiscoveryOff")
    public void killSwitchDisablesRecording(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableDiscovery;
        Distillation.getConfig().enableDiscovery = false;
        try {
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                helper.assertTrue(!DiscoveryManager.record(player, WATER_TO_AWKWARD),
                        "enableDiscovery=false must disable recording");
                helper.assertTrue(DiscoveryManager.data(player).orderedIds().isEmpty(),
                        "nothing may be stored while discovery is disabled");
            } finally {
                player.discard();
            }
            helper.succeed();
        } finally {
            Distillation.getConfig().enableDiscovery = saved;
        }
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationStartDiscovered")
    public void startDiscoveredCompletesTheSetOnJoin(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().startDiscovered;
        Distillation.getConfig().startDiscovered = true;
        try {
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                DiscoveryManager.onJoin(player);
                var graphIds = RecipeGraphs.forLevel(helper.getLevel()).ids();
                helper.assertTrue(DiscoveryManager.data(player).discoveredCount(graphIds) == graphIds.size(),
                        "startDiscovered=true must complete the set on join");
            } finally {
                player.discard();
            }
            helper.succeed();
        } finally {
            Distillation.getConfig().startDiscovered = saved;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void fakePlayerTakeIsInertAndDoesNotBurnTheBrew(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeBrewingStand(helper, new BlockPos(1, 2, 1),
                PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART));

        helper.runAfterDelay(BREW_WAIT, () -> {
            // A machine with a player face: Fabric's real fake player through the real slot path.
            FakePlayer fake = FakePlayer.get(helper.getLevel());
            ItemStack taken = takeBottleSlot(fake, stand, 0);
            helper.assertTrue(!taken.isEmpty(), "the fake take itself must pass through vanilla untouched");
            helper.assertTrue(DiscoveryManager.data(fake).orderedIds().isEmpty(),
                    "a fake player must learn nothing");

            // The guard returned before consuming provenance, so automation didn't burn the
            // brew's teaching: a real player taking the same output still learns it.
            stand.setItem(0, taken);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                takeBottleSlot(player, stand, 0);
                helper.assertTrue(DiscoveryManager.data(player).contains(WATER_TO_AWKWARD),
                        "a real player taking the same output must still learn it");
            } finally {
                player.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void foreignBottleCannotRideOldProvenance(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeBrewingStand(helper, new BlockPos(1, 2, 1),
                PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART));

        helper.runAfterDelay(BREW_WAIT, () -> {
            // Simulate an automation swap: the brewed bottle leaves without onTake firing and a
            // foreign potion lands in the slot that still carries provenance.
            stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));

            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                ItemStack taken = takeBottleSlot(player, stand, 0);
                helper.assertTrue(taken.is(Items.POTION), "the foreign bottle must still be takeable");
                helper.assertTrue(DiscoveryManager.data(player).orderedIds().isEmpty(),
                        "a foreign bottle must not record the old brew's conversion");
            } finally {
                player.discard();
            }
            helper.succeed();
        });
    }

    private static BrewingStandBlockEntity placeBrewingStand(GameTestHelper helper, BlockPos pos, ItemStack bottle,
                                                             ItemStack ingredient) {
        helper.setBlock(pos, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(pos);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    /** Takes a bottle slot's stack through the real menu slot, firing vanilla's {@code onTake} path. */
    private static ItemStack takeBottleSlot(ServerPlayer player, BrewingStandBlockEntity stand, int slot) {
        BrewingStandMenu menu = new BrewingStandMenu(1, player.getInventory(), stand, new SimpleContainerData(2));
        return menu.slots.get(slot).safeTake(64, Integer.MAX_VALUE, player);
    }
}
