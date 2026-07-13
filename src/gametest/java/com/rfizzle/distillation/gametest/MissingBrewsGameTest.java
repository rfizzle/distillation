package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.MurkyDraughtContents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
        assertMurks(helper, distillationBottle("luck"), new ItemStack(Items.GLOWSTONE_DUST),
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
        assertMurks(helper, distillationBottle("glowing"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:glowing");
    }

    // --- Levitation: Awkward + Chorus Fruit -> 0:30 / 1:00, no level II ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void levitationBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.CHORUS_FRUIT), "distillation:levitation");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void levitationExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("levitation"), new ItemStack(Items.REDSTONE),
                "distillation:long_levitation");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowstoneOnLevitationIsAnInvalidPair(GameTestHelper helper) {
        assertMurks(helper, distillationBottle("levitation"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:levitation");
    }

    // --- Health Boost: Awkward + Pumpkin Pie -> 3:00 / 8:00 / II 1:30 ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void healthBoostBrews(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.AWKWARD), new ItemStack(Items.PUMPKIN_PIE), "distillation:health_boost");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void healthBoostExtends(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("health_boost"), new ItemStack(Items.REDSTONE),
                "distillation:long_health_boost");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void healthBoostAmplifies(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("health_boost"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:strong_health_boost");
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

    // --- The completed inversion table (§2): every effect with a sensible opposite corrupts ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void strengthCorruptsToWeakness(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.STRENGTH), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:weakness");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void longStrengthCorruptsToLongWeakness(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.LONG_STRENGTH), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:long_weakness");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void regenerationCorruptsToPoison(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.REGENERATION), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:poison");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void longRegenerationCorruptsToLongPoison(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.LONG_REGENERATION), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:long_poison");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void strongRegenerationCorruptsToStrongPoison(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.STRONG_REGENERATION), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:strong_poison");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void glowingCorruptsToInvisibility(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("glowing"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:invisibility");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void longGlowingCorruptsToLongInvisibility(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("long_glowing"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:long_invisibility");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void slowFallingCorruptsToLevitation(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.SLOW_FALLING), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:levitation");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void longSlowFallingCorruptsToLongLevitation(GameTestHelper helper) {
        assertBrewsTo(helper, bottle(Potions.LONG_SLOW_FALLING), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:long_levitation");
    }

    // --- No sensible opposite: no edge, so the pair is invalid and murks (no false vapor hint) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void resistanceHasNoCorruption(GameTestHelper helper) {
        assertMurks(helper, distillationBottle("resistance"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:resistance");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void absorptionHasNoCorruption(GameTestHelper helper) {
        assertMurks(helper, distillationBottle("absorption"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:absorption");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void healthBoostHasNoCorruption(GameTestHelper helper) {
        assertMurks(helper, distillationBottle("health_boost"), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:health_boost");
    }

    /** Strength and Long Strength corrupt, but Strong Strength has no partner — no Strong Weakness. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void strongStrengthHasNoCorruption(GameTestHelper helper) {
        assertMurks(helper, bottle(Potions.STRONG_STRENGTH), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "minecraft:strong_strength");
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

    /**
     * The pair is invalid: under default config the failed pass bottles a Murky Draught recording
     * the input potion ({@code design/SPEC.md} §1 — the §2 lines' invalid pairs murk like any
     * other; the draughts-off pass-through lives in {@code MurkyDraughtGameTest}).
     */
    private static void assertMurks(GameTestHelper helper, ItemStack bottle, ItemStack ingredient,
                                    String inputPotionId) {
        BrewingStandBlockEntity stand = placeStand(helper, bottle, ingredient);
        helper.runAfterDelay(BREW_WAIT, () -> {
            ItemStack murked = stand.getItem(0);
            helper.assertTrue(murked.is(DistillationItems.MURKY_DRAUGHT),
                    "an invalid pair must bottle a Murky Draught, but found " + murked);
            MurkyDraughtContents contents = murked.get(DistillationItems.MURKY_DRAUGHT_CONTENTS);
            helper.assertTrue(contents != null && contents.inputPotion().toString().equals(inputPotionId),
                    "the draught must record the input potion " + inputPotionId);
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
