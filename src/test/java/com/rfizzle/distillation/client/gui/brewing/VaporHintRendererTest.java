// Tier: 2 (fabric-loader-junit + Bootstrap — vanilla registries, no mod registrations)
package com.rfizzle.distillation.client.gui.brewing;

import com.rfizzle.distillation.recipe.RecipeGraph;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 2 — the vapor-hint resolution and recipes-page list-building decision logic against a real
 * (synthetic) {@link RecipeGraph} and vanilla {@link ItemStack}s: the "all valid pairs discovered"
 * tooltip gate, distinct-output dedup, the non-ingredient/empty short-circuit, discovery-order
 * preservation, stale-id hiding, and row-icon hit-testing.
 */
class VaporHintRendererTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation NETHER_WART_WATER =
            ResourceLocation.parse("distillation:nether_wart/water");
    private static final ResourceLocation NETHER_WART_MUNDANE =
            ResourceLocation.parse("distillation:nether_wart/mundane");

    // nether_wart converts differently per input potion, so one held ingredient can form two
    // distinct valid pairs across two bottles — the shape the "all discovered" gate needs to test.
    private static RecipeGraph graph() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addContainer(Items.POTION);
        builder.addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION);
        builder.addMix(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD);
        builder.addMix(Potions.MUNDANE, Items.NETHER_WART, Potions.SWIFTNESS);
        return RecipeGraph.fromBrewing(builder.build(), Set.of());
    }

    private static ItemStack bottle(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    @Test
    void nonIngredientAndEmptyResolveToNone() {
        RecipeGraph graph = graph();
        assertFalse(VaporHintRenderer.resolve(graph, new ItemStack(Items.STICK),
                List.of(bottle(Potions.WATER)), Set.of()).anyValid());
        assertFalse(VaporHintRenderer.resolve(graph, ItemStack.EMPTY,
                List.of(bottle(Potions.WATER)), Set.of()).anyValid());
    }

    @Test
    void validPairIsUndiscoveredUntilItsIdIsRecorded() {
        RecipeGraph graph = graph();
        List<ItemStack> bottles = List.of(bottle(Potions.WATER));

        VaporHintRenderer.HintResult undiscovered =
                VaporHintRenderer.resolve(graph, new ItemStack(Items.NETHER_WART), bottles, Set.of());
        assertTrue(undiscovered.anyValid());
        assertFalse(undiscovered.allDiscovered(), "no ids discovered yet");
        assertEquals(1, undiscovered.outputNames().size());

        VaporHintRenderer.HintResult discovered = VaporHintRenderer.resolve(
                graph, new ItemStack(Items.NETHER_WART), bottles, Set.of(NETHER_WART_WATER));
        assertTrue(discovered.allDiscovered(), "the one valid pair's id is discovered");
    }

    @Test
    void allDiscoveredRequiresEveryValidPair() {
        RecipeGraph graph = graph();
        List<ItemStack> bottles = List.of(bottle(Potions.WATER), bottle(Potions.MUNDANE));
        ItemStack wart = new ItemStack(Items.NETHER_WART);

        // Two distinct outputs (Awkward, Swiftness) → two distinct colors and names.
        VaporHintRenderer.HintResult both = VaporHintRenderer.resolve(graph, wart, bottles, Set.of());
        assertTrue(both.anyValid());
        assertEquals(2, both.colors().length, "distinct outputs are not merged");
        assertEquals(2, both.outputNames().size());

        // Only one of the two pairs discovered → the tooltip gate stays closed.
        assertFalse(VaporHintRenderer.resolve(graph, wart, bottles, Set.of(NETHER_WART_WATER)).allDiscovered());
        // Both discovered → open.
        assertTrue(VaporHintRenderer.resolve(graph, wart, bottles,
                Set.of(NETHER_WART_WATER, NETHER_WART_MUNDANE)).allDiscovered());
    }

    @Test
    void identicalOutputsDedupe() {
        RecipeGraph graph = graph();
        // Two mundane bottles both brew Swiftness — one color, one name.
        VaporHintRenderer.HintResult result = VaporHintRenderer.resolve(graph, new ItemStack(Items.NETHER_WART),
                List.of(bottle(Potions.MUNDANE), bottle(Potions.MUNDANE)), Set.of());
        assertEquals(1, result.colors().length);
        assertEquals(1, result.outputNames().size());
    }

    @Test
    void visibleConversionsKeepDiscoveryOrderAndHideStaleIds() {
        RecipeGraph graph = graph();
        Set<ResourceLocation> discovered = new LinkedHashSet<>(List.of(
                NETHER_WART_MUNDANE,
                ResourceLocation.parse("distillation:removed/recipe"),
                NETHER_WART_WATER));

        List<RecipeGraph.Conversion> visible = RecipesPageRenderer.visibleConversions(graph, discovered);

        assertEquals(2, visible.size(), "the stale id is hidden");
        assertEquals(NETHER_WART_MUNDANE, visible.get(0).id(), "discovery order is preserved");
        assertEquals(NETHER_WART_WATER, visible.get(1).id());
    }

    @Test
    void stackUnderMouseHitsTheOutputIcon() {
        RecipeGraph graph = graph();
        List<RecipeGraph.Conversion> visible = RecipesPageRenderer.visibleConversions(
                graph, new LinkedHashSet<>(List.of(NETHER_WART_MUNDANE)));
        int screenW = 400;
        int screenH = 300;
        int ox = BrewingStandRecipesLayout.overlayX(screenW);
        int oy = BrewingStandRecipesLayout.overlayY(screenH);
        int rowTop = BrewingStandRecipesLayout.rowTop(oy, 0);

        ItemStack output = RecipesPageRenderer.stackUnderMouse(visible, 0, screenW, screenH,
                ox + BrewingStandRecipesLayout.ROW_OUTPUT_DX + 3, rowTop + 3);
        assertTrue(output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.SWIFTNESS),
                "the output icon carries the conversion's output potion");

        assertTrue(RecipesPageRenderer.stackUnderMouse(visible, 0, screenW, screenH, 0, 0).isEmpty(),
                "a point outside every row icon yields no stack");
    }
}
