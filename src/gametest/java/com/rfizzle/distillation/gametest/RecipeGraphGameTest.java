package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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

/**
 * The live recipe graph and registrations on a real server: vanilla, Distillation, and container
 * conversions all present with their stable ids (proving the PotionBrewing → graph ingestion end
 * to end), the registered potions carrying the SPEC §2 durations, and the
 * {@code enableMissingBrews=false} contract — Distillation's lines leave the graph and stop
 * brewing while vanilla brewing stays byte-identical.
 */
public class RecipeGraphGameTest implements FabricGameTest {

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
        assertContains(helper, graph, "distillation:chorus_fruit/awkward");
        assertContains(helper, graph, "distillation:pumpkin_pie/awkward");
        assertContains(helper, graph, "distillation:honey_bottle/swiftness");
        assertContains(helper, graph, "distillation:redstone/distillation/haste");
        assertContains(helper, graph, "distillation:fermented_spider_eye/mundane");
        // The completed inversion table (§2): a vanilla-input corruption and a mod-input one (the
        // latter deriving a prefixed segment) both ingest through the same vanilla registry.
        assertContains(helper, graph, "distillation:fermented_spider_eye/strength");
        assertContains(helper, graph, "distillation:fermented_spider_eye/distillation/glowing");
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
     * Flips the live server config, so it resolves the contract <em>synchronously</em> — the lines
     * leave the graph, their ingredient stops being a graph ingredient, and vanilla brewing still
     * resolves — and restores the flag in the same invocation. A delayed brew here would race the
     * concurrent {@code /distillation reload} test, which re-reads the config from disk and would
     * re-enable the lines mid-window.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationToggle")
    public void toggleOffRemovesTheLinesAndKeepsVanillaBrewing(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableMissingBrews;
        Distillation.getConfig().enableMissingBrews = false;
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:shulker_shell/awkward")),
                    "with enableMissingBrews=false the missing-brew lines leave the graph");
            // The completed inversion table rides the same kill switch — the corruption edges leave too.
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:fermented_spider_eye/strength")),
                    "with enableMissingBrews=false the mod's corruption edges leave the graph");
            helper.assertTrue(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")),
                    "vanilla conversions stay in the graph");
            // The line's premium concentration goes with it, so the reagent leaves the graph entirely.
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:shulker_shell/distillation/resistance")),
                    "the disabled line's premium concentration leaves the graph too");
            // A disabled line no longer brews: its ingredient leaves the graph entirely, so no cycle
            // ever starts over it and it resolves to nothing.
            helper.assertTrue(!graph.isIngredient(new ItemStack(Items.SHULKER_SHELL)),
                    "a disabled line's ingredient is no longer a graph ingredient");
            helper.assertTrue(graph.matchConversion(new ItemStack(Items.SHULKER_SHELL),
                            PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)) == null,
                    "a disabled line no longer resolves to its output");
            // Vanilla brewing stays byte-identical: Water + Nether Wart still resolves to Awkward.
            helper.assertTrue(graph.matchConversion(new ItemStack(Items.NETHER_WART),
                            PotionContents.createItemStack(Items.POTION, Potions.WATER)) != null,
                    "vanilla brewing must stay intact while the toggle is off");
        } finally {
            Distillation.getConfig().enableMissingBrews = saved;
        }
        helper.succeed();
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
