// Tier: 2 (fabric-loader-junit + Bootstrap — vanilla registries, no mod registrations)
package com.rfizzle.distillation.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graph construction and per-bottle resolution against synthetic brewing registries, built through
 * vanilla's own public {@link PotionBrewing.Builder} — the same shape third-party registrations
 * take, ingested with zero Distillation-side code. Resolution semantics are pinned against
 * {@code PotionBrewing.mix}/{@code hasMix}: unmatched bottles pass through unchanged (same
 * reference), container conversions outrank potion conversions, and non-container bottles never
 * gate a cycle.
 */
class RecipeGraphTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PotionBrewing syntheticRegistry() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addContainer(Items.POTION);
        builder.addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION);
        builder.addMix(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD);
        return builder.build();
    }

    private static ItemStack bottleOf(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    @Test
    void ingestsAThirdPartyShapedRegistryWithStableIds() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        assertTrue(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")),
                "potion conversion carries its stable id");
        assertTrue(graph.contains(ResourceLocation.parse("distillation:gunpowder/potion")),
                "container conversion carries its stable id");
        assertEquals(2, graph.conversions().size());
    }

    @Test
    void ingredientMembershipMatchesVanilla() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        assertTrue(graph.isIngredient(new ItemStack(Items.NETHER_WART)));
        assertTrue(graph.isIngredient(new ItemStack(Items.GUNPOWDER)));
        assertFalse(graph.isIngredient(new ItemStack(Items.STICK)));
    }

    @Test
    void validPairResolvesToTheOutputPotion() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        ItemStack brewed = graph.resolve(new ItemStack(Items.NETHER_WART), bottleOf(Potions.WATER));

        assertSame(Items.POTION, brewed.getItem(), "potion conversion keeps the bottle item");
        assertTrue(brewed.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .is(Potions.AWKWARD), "bottle resolves to the conversion's output potion");
    }

    @Test
    void invalidPairPassesThroughUnchanged() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());
        ItemStack awkward = bottleOf(Potions.AWKWARD);

        assertSame(awkward, graph.resolve(new ItemStack(Items.NETHER_WART), awkward),
                "a bottle with no conversion for this ingredient is returned by reference, untouched");
    }

    @Test
    void emptyAndContentlessBottlesPassThroughUnchanged() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());
        ItemStack stick = new ItemStack(Items.STICK);

        assertSame(ItemStack.EMPTY, graph.resolve(new ItemStack(Items.NETHER_WART), ItemStack.EMPTY));
        assertSame(stick, graph.resolve(new ItemStack(Items.NETHER_WART), stick),
                "a stack with no potion contents never converts");
    }

    @Test
    void containerConversionOutranksPotionConversion() {
        // Synthetic overlap: gunpowder serves both a container conversion and a potion conversion.
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addContainer(Items.POTION);
        builder.addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION);
        builder.addMix(Potions.WATER, Items.GUNPOWDER, Potions.AWKWARD);
        RecipeGraph graph = RecipeGraph.fromBrewing(builder.build(), Set.of());

        ItemStack brewed = graph.resolve(new ItemStack(Items.GUNPOWDER), bottleOf(Potions.WATER));

        assertSame(Items.SPLASH_POTION, brewed.getItem(),
                "container conversions are checked first, as in PotionBrewing.mix");
        assertTrue(brewed.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .is(Potions.WATER), "container conversion keeps the potion payload");
    }

    @Test
    void canBrewRequiresARecognizedContainer() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());
        ItemStack ingredient = new ItemStack(Items.NETHER_WART);

        assertTrue(graph.canBrew(bottleOf(Potions.WATER), ingredient));
        // Same potion payload in a splash bottle: not in the containers list, so no cycle —
        // mirrors PotionBrewing.hasMix's isContainer gate.
        ItemStack splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER);
        assertFalse(graph.canBrew(splash, ingredient));
    }

    @Test
    void receptiveMeansAPotionBearingContainer() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        assertTrue(graph.isReceptive(bottleOf(Potions.WATER)));
        assertTrue(graph.isReceptive(bottleOf(Potions.AWKWARD)),
                "receptivity is about the container, not whether any pair is valid");
        assertFalse(graph.isReceptive(ItemStack.EMPTY));
        assertFalse(graph.isReceptive(new ItemStack(Items.GLASS_BOTTLE)),
                "a contentless non-container never gates a cycle");
        assertFalse(graph.isReceptive(new ItemStack(Items.POTION)),
                "a container item stripped of potion contents is not receptive");
        assertFalse(graph.isReceptive(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER)),
                "an item outside the registry's containers list is not receptive");
    }

    @Test
    void hintCandidatesAreExactlyTheConversionsThatWouldTake() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        var waterCandidates = graph.conversionsFor(bottleOf(Potions.WATER));
        assertEquals(2, waterCandidates.size(),
                "water takes the nether-wart mix and the gunpowder container conversion");
        assertTrue(waterCandidates.stream().allMatch(conversion ->
                        conversion.ingredient() == Items.NETHER_WART || conversion.ingredient() == Items.GUNPOWDER),
                "every candidate's ingredient genuinely would have taken");

        var splashCandidates = graph.conversionsFor(
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
        assertEquals(1, splashCandidates.size(),
                "a splash bottle keeps the potion mix but not the potion-item container conversion");
        assertSame(Items.NETHER_WART, splashCandidates.get(0).ingredient());

        // A drinkable awkward bottle still takes gunpowder (the container conversion); the truly
        // hintless shape is a bottle whose item has no container conversions AND whose potion has
        // no onward mixes.
        assertTrue(graph.conversionsFor(
                        PotionContents.createItemStack(Items.SPLASH_POTION, Potions.AWKWARD)).isEmpty(),
                "a bottle nothing brews onward from has no candidates — the hintless draught");
        assertTrue(graph.conversionsFor(ItemStack.EMPTY).isEmpty());
        assertTrue(graph.conversionsFor(new ItemStack(Items.STICK)).isEmpty());
    }

    @Test
    void murkyHintPrefersPotionConversionsOverContainers() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());

        // Water takes both nether wart (a potion conversion → awkward) and gunpowder (a container
        // conversion → splash). The hint must always name the new liquid, never the container swap.
        var waterCandidates = graph.conversionsFor(bottleOf(Potions.WATER));
        for (long seed = 0; seed < 200; seed++) {
            var hint = MurkyHints.select(waterCandidates, seed,
                    conversion -> conversion instanceof RecipeGraph.PotionConversion);
            assertTrue(hint.isPresent() && hint.get().ingredient() == Items.NETHER_WART,
                    "a bottle with both kinds of candidate never hints the container ingredient (seed " + seed + ")");
        }

        // A drinkable awkward bottle only takes gunpowder (container conversion); with no potion
        // conversion to prefer, the container hint stands — better than a hintless draught.
        var awkwardCandidates = graph.conversionsFor(bottleOf(Potions.AWKWARD));
        for (long seed = 0; seed < 200; seed++) {
            var hint = MurkyHints.select(awkwardCandidates, seed,
                    conversion -> conversion instanceof RecipeGraph.PotionConversion);
            assertTrue(hint.isPresent() && hint.get().ingredient() == Items.GUNPOWDER,
                    "a container-only bottle still hints its container conversion (seed " + seed + ")");
        }
    }

    @Test
    void flickerResolvesTheHintedConversionsOutput() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(), Set.of());
        ResourceLocation water = ResourceLocation.parse("minecraft:water");

        var potionHint = MurkyHints.flickerPotion(graph, water, ResourceLocation.parse("minecraft:nether_wart"));
        assertTrue(potionHint.isPresent() && potionHint.get().is(Potions.AWKWARD),
                "a potion-conversion hint flickers as that conversion's output");

        var containerHint = MurkyHints.flickerPotion(graph, water, ResourceLocation.parse("minecraft:gunpowder"));
        assertTrue(containerHint.isPresent() && containerHint.get().is(Potions.WATER),
                "a container-conversion hint keeps the input potion — the liquid never changed");

        assertTrue(MurkyHints.flickerPotion(graph, ResourceLocation.parse("minecraft:awkward"),
                        ResourceLocation.parse("minecraft:nether_wart")).isEmpty(),
                "a hint the graph no longer resolves for this input flickers as nothing");
        assertTrue(MurkyHints.flickerPotion(graph, water, ResourceLocation.parse("minecraft:stick")).isEmpty(),
                "an ingredient with no conversions flickers as nothing");
    }

    @Test
    void excludedIdsLeaveTheGraph() {
        RecipeGraph graph = RecipeGraph.fromBrewing(syntheticRegistry(),
                Set.of(ResourceLocation.parse("distillation:nether_wart/water")));

        assertFalse(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")));
        assertFalse(graph.isIngredient(new ItemStack(Items.NETHER_WART)),
                "an item whose only conversion is excluded is no longer a graph ingredient");
        ItemStack water = bottleOf(Potions.WATER);
        assertSame(water, graph.resolve(new ItemStack(Items.NETHER_WART), water),
                "an excluded conversion no longer brews");
        assertTrue(graph.contains(ResourceLocation.parse("distillation:gunpowder/potion")),
                "unrelated conversions survive the exclusion");
    }
}
