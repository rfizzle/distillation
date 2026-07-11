package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.brew.DistillationPotions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * End-to-end brews of the SPEC §2 lines at a real stand: each test loads a stand, waits out the
 * full 400-tick cycle, and asserts the bottle slot's potion — exercising the brew seam mixin, the
 * recipe graph, and the registrations together.
 */
public class MissingBrewsGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);
    /** Fuel loads on the first tick, the 400-tick cycle starts the same tick; margin for safety. */
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 500;

    // --- Resistance: Awkward + Shulker Shell -> 3:00 / 8:00 / II 1:30 ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void resistanceBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.SHULKER_SHELL), "distillation:resistance");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void resistanceExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("resistance"), new ItemStack(Items.REDSTONE),
                "distillation:long_resistance");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void resistanceAmplifies(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("resistance"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:strong_resistance");
    }

    // --- Haste: Potion of Swiftness + Honey Bottle -> 8:00 / 20:00 / II 4:00 ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void hasteBrewsAndConsumesHoneyBottleWhole(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper, bottle(Potions.SWIFTNESS),
                new ItemStack(Items.HONEY_BOTTLE));
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), "distillation:haste");
            helper.assertTrue(stand.getItem(3).isEmpty(),
                    "honey bottle is consumed whole - no glass bottle back in the ingredient slot");
            helper.assertItemEntityNotPresent(Items.GLASS_BOTTLE, STAND, 5.0);
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void hasteExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("haste"), new ItemStack(Items.REDSTONE), "distillation:long_haste");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void hasteAmplifies(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("haste"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:strong_haste");
    }

    // --- Absorption: Awkward + Golden Apple -> 3:00 / 8:00 / II 1:30 ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void absorptionBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.GOLDEN_APPLE), "distillation:absorption");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void absorptionExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("absorption"), new ItemStack(Items.REDSTONE),
                "distillation:long_absorption");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void absorptionAmplifies(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("absorption"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:strong_absorption");
    }

    // --- Luck: Awkward + Nautilus Shell -> 8:00 / 20:00, no level II ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void luckBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.NAUTILUS_SHELL), "distillation:luck");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void luckExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("luck"), new ItemStack(Items.REDSTONE), "distillation:long_luck");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowstoneOnLuckIsAnInvalidPair(GameTestHelper helper) {
        assertNothingBrews(helper, distillationBottle("luck"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:luck");
    }

    // --- Glowing: Awkward + Glow Ink Sac -> 3:00 / 8:00, no level II ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowingBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.GLOW_INK_SAC), "distillation:glowing");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowingExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("glowing"), new ItemStack(Items.REDSTONE),
                "distillation:long_glowing");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowstoneOnGlowingIsAnInvalidPair(GameTestHelper helper) {
        assertNothingBrews(helper, distillationBottle("glowing"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:glowing");
    }

    // --- Corruptions and the Mundane arrow ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void hasteCorruptsToMiningFatigue(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("haste"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:mining_fatigue");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void luckCorruptsToBadLuck(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("luck"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:bad_luck");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void mundaneFermentsToWeakness(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.MUNDANE), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:weakness");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void waterWeaknessRouteStaysIntact(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.WATER), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:weakness");
    }

    // --- helpers ---

    private static ItemStack bottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static ItemStack distillationBottle(String path) {
        return PotionContents.createItemStack(Items.POTION, DistillationPotions.potion(path));
    }

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, ItemStack bottle, ItemStack ingredient) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static void assertBrewsTo(GameTestHelper helper, ItemStack bottle, ItemStack ingredient,
                                      String expectedPotionId) {
        BrewingStandBlockEntity stand = placeStand(helper, bottle, ingredient);
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), expectedPotionId);
            helper.succeed();
        });
    }

    /** The pair is invalid: no cycle starts, the bottle and the ingredient survive untouched. */
    private static void assertNothingBrews(GameTestHelper helper, ItemStack bottle, ItemStack ingredient,
                                           String unchangedPotionId) {
        Item ingredientItem = ingredient.getItem();
        BrewingStandBlockEntity stand = placeStand(helper, bottle, ingredient);
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), unchangedPotionId);
            helper.assertTrue(stand.getItem(3).is(ingredientItem) && stand.getItem(3).getCount() == 1,
                    "an invalid pair must not consume the ingredient");
            helper.succeed();
        });
    }

    private static void assertPotion(GameTestHelper helper, ItemStack stack, String expectedPotionId) {
        ResourceLocation actual = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(key -> key.location())
                .orElse(null);
        helper.assertTrue(actual != null && actual.toString().equals(expectedPotionId),
                "expected bottle to hold " + expectedPotionId + " but found " + actual);
    }
}
