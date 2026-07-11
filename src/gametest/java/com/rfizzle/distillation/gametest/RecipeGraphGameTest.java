package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * The live recipe graph and registrations on a real server: vanilla, Distillation, and container
 * conversions all present with their stable ids (proving the PotionBrewing → graph ingestion end
 * to end), the registered potions carrying the SPEC §2 durations, and the
 * {@code enableMissingBrews=false} contract — Distillation's lines leave the graph and stop
 * brewing while vanilla brewing stays byte-identical.
 */
public class RecipeGraphGameTest implements FabricGameTest {

    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 500;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void liveGraphHoldsVanillaOwnAndContainerConversions(GameTestHelper helper) {
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());

        // Vanilla potion mix, registered by vanilla's own bootstrap.
        assertContains(helper, graph, "distillation:nether_wart/water");
        // Container conversions are graph entries like any other (SPEC §1).
        assertContains(helper, graph, "distillation:gunpowder/potion");
        assertContains(helper, graph, "distillation:dragon_breath/splash_potion");
        // Distillation's own lines arrive through the same vanilla registry — including a
        // non-minecraft input namespace deriving a prefixed segment.
        assertContains(helper, graph, "distillation:shulker_shell/awkward");
        assertContains(helper, graph, "distillation:honey_bottle/swiftness");
        assertContains(helper, graph, "distillation:redstone/distillation/haste");
        assertContains(helper, graph, "distillation:fermented_spider_eye/mundane");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registeredPotionsCarrySpecDurations(GameTestHelper helper) {
        for (DistillationPotions.Line line : DistillationPotions.LINES) {
            assertEffect(helper, line.path(), line.baseTicks(), 0);
            assertEffect(helper, "long_" + line.path(), line.longTicks(), 0);
            if (line.hasStrong()) {
                assertEffect(helper, "strong_" + line.path(), line.strongTicks(), 1);
            }
        }
        helper.succeed();
    }

    /**
     * Own batch: this test flips the live server config, so it must never overlap the brew tests
     * running under defaults.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT, batch = "distillationToggle")
    public void toggleOffRemovesTheLinesAndKeepsVanillaBrewing(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableMissingBrews;
        Distillation.getConfig().enableMissingBrews = false;
        // The flipped config lives past this method (the delayed closure), so a plain finally
        // can't scope it: the catch restores on any synchronous failure, and the closure restores
        // before asserting — either way no other test ever observes the flipped value.
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:shulker_shell/awkward")),
                    "with enableMissingBrews=false the five lines leave the graph");
            helper.assertTrue(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")),
                    "vanilla conversions stay in the graph");

            // Stand A: a Distillation line - must not even start a cycle. Stand B: vanilla brewing.
            BrewingStandBlockEntity disabled = placeStand(helper, new BlockPos(1, 2, 1),
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD), new ItemStack(Items.SHULKER_SHELL));
            BrewingStandBlockEntity vanilla = placeStand(helper, new BlockPos(3, 2, 1),
                    PotionContents.createItemStack(Items.POTION, Potions.WATER), new ItemStack(Items.NETHER_WART));

            helper.runAfterDelay(BREW_WAIT, () -> {
                Distillation.getConfig().enableMissingBrews = saved;
                helper.assertTrue(potionIdOf(disabled.getItem(0)).equals("minecraft:awkward"),
                        "disabled line must leave the bottle untouched");
                helper.assertTrue(disabled.getItem(3).is(Items.SHULKER_SHELL) && disabled.getItem(3).getCount() == 1,
                        "disabled line must not consume the ingredient");
                helper.assertTrue(potionIdOf(vanilla.getItem(0)).equals("minecraft:awkward"),
                        "vanilla brewing must stay intact while the toggle is off");
                helper.succeed();
            });
        } catch (Throwable t) {
            Distillation.getConfig().enableMissingBrews = saved;
            throw t;
        }
    }

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, BlockPos pos, ItemStack bottle,
                                                      ItemStack ingredient) {
        helper.setBlock(pos, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(pos);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static String potionIdOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(net.minecraft.core.Holder::unwrapKey)
                .map(key -> key.location().toString())
                .orElse("<none>");
    }

    private static void assertContains(GameTestHelper helper, RecipeGraph graph, String recipeId) {
        helper.assertTrue(graph.contains(ResourceLocation.parse(recipeId)),
                "recipe graph is missing " + recipeId);
    }

    private static void assertEffect(GameTestHelper helper, String path, int expectedTicks, int expectedAmplifier) {
        Potion potion = BuiltInRegistries.POTION.getOptional(Distillation.id(path))
                .orElse(null);
        helper.assertTrue(potion != null, "potion not registered: distillation:" + path);
        helper.assertTrue(potion.getEffects().size() == 1, path + " carries exactly one effect");
        MobEffectInstance effect = potion.getEffects().get(0);
        helper.assertTrue(effect.getDuration() == expectedTicks,
                path + " duration: expected " + expectedTicks + ", got " + effect.getDuration());
        helper.assertTrue(effect.getAmplifier() == expectedAmplifier,
                path + " amplifier: expected " + expectedAmplifier + ", got " + effect.getAmplifier());
    }
}
