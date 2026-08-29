package com.rfizzle.distillation.data;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.item.DistillationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Distillation's one crafting recipe: the Flask ({@code design/SPEC.md} §12) — a glass bottle
 * cased in copper.
 *
 * <p>Written through {@link RecipeOutput#accept} with a {@code null} advancement rather than
 * {@link net.minecraft.data.recipes.ShapedRecipeBuilder}, because the builder's {@code save} also
 * emits a recipe-unlock advancement under {@code advancement/recipes/equipment/flask.json} and
 * Distillation has never shipped one. Adding it would be a live gameplay change — a recipe-book
 * unlock and its toast where there was none — smuggled in under a datagen conversion, so the
 * conversion matches the file that ships instead. The builder cannot be used without it:
 * {@code ensureValid} rejects a recipe with no unlock criterion outright.
 *
 * <p>The ingredient key is a {@link LinkedHashMap} rather than {@link Map#of}: {@code Map.of}
 * randomizes its iteration order per JVM, which would make the emitted key block differ between
 * two runs and fail {@code verifyDatagenIdempotent} at random.
 */
public class DistillationRecipeProvider extends FabricRecipeProvider {

    public DistillationRecipeProvider(FabricDataOutput output,
                                      CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void buildRecipes(RecipeOutput exporter) {
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('C', Ingredient.of(Items.COPPER_INGOT));
        key.put('G', Ingredient.of(Items.GLASS_BOTTLE));

        ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of(
                "C C",
                "CGC",
                " C "));

        exporter.accept(
                Distillation.id("flask"),
                new ShapedRecipe("", CraftingBookCategory.EQUIPMENT, pattern,
                        new ItemStack(DistillationItems.FLASK)),
                null);
    }

    @Override
    public String getName() {
        return "Distillation Recipes";
    }
}
