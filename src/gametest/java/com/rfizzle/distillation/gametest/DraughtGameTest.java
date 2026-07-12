package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.item.DistillationItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The Honest Durations &amp; Draughts contract of {@code design/SPEC.md} §4 on a live server: utility
 * potions apply their retuned durations while combat potions keep vanilla timers; sneaking sips a
 * full potion into a marked half that renders and drinks its remainder for the glass bottle; instant
 * potions sneak-drink whole; half draughts are rejected as stand inputs and inert to brewing; and
 * both toggles restore vanilla behavior independently.
 */
public class DraughtGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 600;
    private static final int TOLERANCE = 20;

    private static final int FIRE_RES_HONEST = 9600;   // SPEC §4: 8:00
    private static final int FIRE_RES_HALF = 4800;     // ⌊9600 ÷ 2⌋
    private static final int FIRE_RES_VANILLA = 3600;  // vanilla 3:00, honest off
    private static final int STRENGTH_VANILLA = 3600;  // combat class — untouched

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void utilityPotionRetunesButCombatPotionDoesNot(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            potion("minecraft:fire_resistance").getItem()
                    .finishUsingItem(potion("minecraft:fire_resistance"), helper.getLevel(), player);
            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_HONEST,
                    "Fire Resistance must retune to the honest 8:00");

            potion("minecraft:strength").getItem()
                    .finishUsingItem(potion("minecraft:strength"), helper.getLevel(), player);
            assertDuration(helper, player.getEffect(MobEffects.DAMAGE_BOOST), STRENGTH_VANILLA,
                    "Strength is combat class and must keep its vanilla timer");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sneakingSipsAHalf(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setShiftKeyDown(true);
        try {
            ItemStack full = potion("minecraft:fire_resistance");
            ItemStack returned = full.getItem().finishUsingItem(full, helper.getLevel(), player);

            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_HALF,
                    "a sip must apply ⌊honest ÷ 2⌋");
            helper.assertTrue(returned.is(Items.POTION) && returned.has(DistillationItems.DRAUGHT),
                    "the sipped potion must become a marked half draught in hand");
            helper.assertTrue(!returned.is(Items.GLASS_BOTTLE),
                    "a sip must not return the glass bottle — the half stays");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drinkingAHalfReturnsTheBottle(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack half = half("minecraft:fire_resistance");
            ItemStack returned = half.getItem().finishUsingItem(half, helper.getLevel(), player);

            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_HALF,
                    "drinking the half applies the remaining ⌊honest ÷ 2⌋");
            helper.assertTrue(returned.is(Items.GLASS_BOTTLE),
                    "drinking a half must return the glass bottle");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void instantPotionSneakDrinksWhole(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setShiftKeyDown(true);
        player.setHealth(4.0F);
        try {
            ItemStack healing = potion("minecraft:healing");
            ItemStack returned = healing.getItem().finishUsingItem(healing, helper.getLevel(), player);

            helper.assertTrue(player.getHealth() > 4.0F,
                    "an instant potion must apply whole, not sipped");
            helper.assertTrue(returned.is(Items.GLASS_BOTTLE) && !returned.has(DistillationItems.DRAUGHT),
                    "an instant potion sneak-drinks whole and returns the bottle, never a half");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void utilityPotionRetunesForTooltipAndThrownPaths(GameTestHelper helper) {
        // getAllEffects drives tooltip rendering and thrown-potion application (SPEC §4).
        MobEffectInstance effect = potion("minecraft:fire_resistance")
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .getAllEffects().iterator().next();
        helper.assertTrue(Math.abs(effect.getDuration() - FIRE_RES_HONEST) <= TOLERANCE,
                "getAllEffects must surface the honest duration, was " + effect.getDuration());
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void halfDraughtTooltipHalvesTheDuration(GameTestHelper helper) {
        helper.assertTrue(tooltipHasDuration(potion("minecraft:fire_resistance"), "8:00"),
                "a full utility potion's tooltip shows the honest 8:00");
        helper.assertTrue(tooltipHasDuration(half("minecraft:fire_resistance"), "4:00"),
                "a half draught's tooltip shows the halved 4:00");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeSneakDrinksWhole(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        player.setShiftKeyDown(true);
        try {
            ItemStack full = potion("minecraft:fire_resistance");
            ItemStack returned = full.getItem().finishUsingItem(full, helper.getLevel(), player);
            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_HONEST,
                    "a creative sneak-drink applies the full duration, not a half");
            helper.assertTrue(!returned.has(DistillationItems.DRAUGHT),
                    "a creative sneak-drink must not leave a half draught (nothing is consumed to keep)");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void halfDraughtIsRejectedAndInertAtTheStand(GameTestHelper helper) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        helper.assertTrue(!stand.canPlaceItem(0, half("minecraft:fire_resistance")),
                "a half draught must be rejected by the bottle slots");
        helper.assertTrue(!stand.canPlaceItem(BatchBrew.FIRST_BATCH_SLOT, half("minecraft:fire_resistance")),
                "a half draught must be rejected by the batch row too (no topping up)");

        // Even forced into a slot, it is not receptive: no cycle starts and nothing brews.
        stand.setItem(0, half("minecraft:awkward"));
        stand.setItem(3, new ItemStack(Items.NETHER_WART));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(stand.getItem(0).has(DistillationItems.DRAUGHT),
                    "a half draught must never brew onward");
            helper.assertTrue(stand.getItem(3).getCount() == 1,
                    "no cycle may start over a half-draught-only stand");
            helper.succeed();
        });
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationHonestOff")
    public void honestDurationsOffRestoresVanillaTimers(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableHonestDurations;
        Distillation.getConfig().enableHonestDurations = false;
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack full = potion("minecraft:fire_resistance");
            full.getItem().finishUsingItem(full, helper.getLevel(), player);
            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_VANILLA,
                    "with honest durations off, Fire Resistance keeps its vanilla 3:00");
        } finally {
            Distillation.getConfig().enableHonestDurations = saved;
            player.discard();
        }
        helper.succeed();
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationDraughtsOff")
    public void draughtsOffDrinksFullButExistingHalvesRemain(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableDraughts;
        Distillation.getConfig().enableDraughts = false;
        ServerPlayer player = survivalPlayer(helper);
        player.setShiftKeyDown(true);
        try {
            // Sneaking on a full potion now drinks it whole — no half is created.
            ItemStack full = potion("minecraft:fire_resistance");
            ItemStack fromFull = full.getItem().finishUsingItem(full, helper.getLevel(), player);
            helper.assertTrue(fromFull.is(Items.GLASS_BOTTLE) && !fromFull.has(DistillationItems.DRAUGHT),
                    "with draughts off, sneaking drinks the whole potion");
            assertDuration(helper, player.getEffect(MobEffects.FIRE_RESISTANCE), FIRE_RES_HONEST,
                    "with draughts off, a whole drink applies the full honest duration");

            // An existing half is still drinkable for its stored half.
            ItemStack half = half("minecraft:fire_resistance");
            ItemStack fromHalf = half.getItem().finishUsingItem(half, helper.getLevel(), player);
            helper.assertTrue(fromHalf.is(Items.GLASS_BOTTLE),
                    "an existing half draught must still drink for the glass bottle");
        } finally {
            Distillation.getConfig().enableDraughts = saved;
            player.discard();
        }
        helper.succeed();
    }

    // --- helpers ---

    private static ItemStack potion(String potionId) {
        var holder = BuiltInRegistries.POTION
                .getHolder(ResourceKey.create(Registries.POTION, ResourceLocation.parse(potionId)))
                .orElseThrow();
        return PotionContents.createItemStack(Items.POTION, holder);
    }

    private static ItemStack half(String potionId) {
        ItemStack stack = potion(potionId);
        stack.set(DistillationItems.DRAUGHT, true);
        return stack;
    }

    private static boolean tooltipHasDuration(ItemStack stack, String needle) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.Default.NORMAL);
        return lines.stream().anyMatch(line -> line.getString().contains(needle));
    }

    private static void assertDuration(GameTestHelper helper, MobEffectInstance effect, int expected, String message) {
        helper.assertTrue(effect != null, message + " (effect absent)");
        int duration = effect.getDuration();
        helper.assertTrue(Math.abs(duration - expected) <= TOLERANCE,
                message + " — expected ~" + expected + " but was " + duration);
    }

    /**
     * A connected survival-mode player: effects need a live connection, and drinking needs
     * consumption — survival abilities make {@code hasInfiniteMaterials()} false, so the stack
     * shrinks (or converts to a half) and the bottle comes back.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
