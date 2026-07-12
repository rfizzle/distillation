// Tier: 2 (fabric-loader-junit + Bootstrap — building conversion icons touches vanilla potion holders)
package com.rfizzle.distillation.compat.viewer;

import com.rfizzle.distillation.recipe.RecipeGraph;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the recipe-viewer filter core ({@code design/SPEC.md} §Compatibility): conversions dedupe by
 * id, a null discovery set shows everything ({@code recipeViewerShowsUndiscovered}), and a discovery
 * set hides everything outside it — the "never spoil what the stand teaches" default.
 */
class BrewingViewerRecipesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RecipeGraph.Conversion conversion(String id) {
        return new RecipeGraph.PotionConversion(ResourceLocation.fromNamespaceAndPath("distillation", id),
                Items.NETHER_WART, Potions.WATER, Potions.AWKWARD);
    }

    @Test
    void nullDiscoveryShowsEveryConversionOnce() {
        List<RecipeGraph.Conversion> conversions = List.of(
                conversion("a"), conversion("b"),
                // A second conversion sharing id "a" — the builder is first-wins, the viewer shows it once.
                new RecipeGraph.PotionConversion(ResourceLocation.fromNamespaceAndPath("distillation", "a"),
                        Items.GLOWSTONE_DUST, Potions.WATER, Potions.THICK));
        List<BrewingViewerRecipes.Entry> entries = BrewingViewerRecipes.entriesFrom(conversions, null);
        assertEquals(2, entries.size(), "showing all, deduped by id");
        assertEquals(List.of(
                        ResourceLocation.fromNamespaceAndPath("distillation", "a"),
                        ResourceLocation.fromNamespaceAndPath("distillation", "b")),
                entries.stream().map(BrewingViewerRecipes.Entry::id).toList(), "in graph order");
    }

    @Test
    void discoverySetHidesUndiscoveredConversions() {
        List<RecipeGraph.Conversion> conversions = List.of(conversion("a"), conversion("b"));
        List<BrewingViewerRecipes.Entry> onlyA = BrewingViewerRecipes.entriesFrom(conversions,
                Set.of(ResourceLocation.fromNamespaceAndPath("distillation", "a")));
        assertEquals(1, onlyA.size(), "only the discovered conversion renders");
        assertEquals(ResourceLocation.fromNamespaceAndPath("distillation", "a"), onlyA.get(0).id());

        assertEquals(0, BrewingViewerRecipes.entriesFrom(conversions, Set.of()).size(),
                "an empty discovery set hides everything");
    }
}
