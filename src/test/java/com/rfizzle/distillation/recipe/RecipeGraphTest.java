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
