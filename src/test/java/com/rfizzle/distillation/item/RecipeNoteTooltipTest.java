// Tier: 2 (fabric-loader-junit + Bootstrap — resolves vanilla potion/item holders for the names)
package com.rfizzle.distillation.item;

import com.rfizzle.distillation.recipe.RecipeGraph;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier 2 — the recipe-note tooltip's name derivation against a real (synthetic) graph: a potion
 * conversion reads through its input/output <em>potion</em> names, a container conversion (gunpowder
 * → splash) reads through its plain item names, and the reminder line always follows. The tooltip's
 * grammar and its per-kind branch are pinned here so a rename or a swapped stack can't ship a note
 * that names the wrong thing.
 */
class RecipeNoteTooltipTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation NETHER_WART_WATER =
            ResourceLocation.parse("distillation:nether_wart/water");
    private static final ResourceLocation GUNPOWDER_POTION =
            ResourceLocation.parse("distillation:gunpowder/potion");

    private static RecipeGraph graph() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        builder.addContainer(Items.POTION);
        builder.addContainerRecipe(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION);
        builder.addMix(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD);
        return RecipeGraph.fromBrewing(builder.build(), Set.of());
    }

    private static Object[] recipeLineArgs(RecipeGraph.Conversion conversion) {
        List<Component> lines = RecipeNoteItem.recipeTooltip(conversion);
        assertEquals(2, lines.size(), "a resolved note shows the recipe line and the brew-it reminder");
        assertEquals("tooltip.distillation.recipe_note.hint",
                ((TranslatableContents) lines.get(1).getContents()).getKey(),
                "the second line is the brew-it reminder");
        TranslatableContents recipe = (TranslatableContents) lines.get(0).getContents();
        assertEquals("tooltip.distillation.recipe_note.recipe", recipe.getKey());
        assertEquals(3, recipe.getArgs().length, "the recipe line names input, ingredient, and output");
        return recipe.getArgs();
    }

    @Test
    void potionConversionNamesItsInputAndOutputPotions() {
        RecipeGraph.Conversion conversion = graph().conversionById(NETHER_WART_WATER).orElseThrow();
        Object[] args = recipeLineArgs(conversion);

        assertEquals(PotionContents.createItemStack(Items.POTION, Potions.WATER).getHoverName(), args[0],
                "the input is named as its potion (Water)");
        assertEquals(new ItemStack(Items.NETHER_WART).getHoverName(), args[1], "the ingredient is the item");
        assertEquals(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD).getHoverName(), args[2],
                "the output is named as its potion (Awkward)");
    }

    @Test
    void containerConversionNamesItsPlainItems() {
        RecipeGraph.Conversion conversion = graph().conversionById(GUNPOWDER_POTION).orElseThrow();
        Object[] args = recipeLineArgs(conversion);

        assertEquals(new ItemStack(Items.POTION).getHoverName(), args[0],
                "a container conversion's input is the plain potion item");
        assertEquals(new ItemStack(Items.GUNPOWDER).getHoverName(), args[1], "the ingredient is the item");
        assertEquals(new ItemStack(Items.SPLASH_POTION).getHoverName(), args[2],
                "the output is the plain splash-potion item");
    }
}
