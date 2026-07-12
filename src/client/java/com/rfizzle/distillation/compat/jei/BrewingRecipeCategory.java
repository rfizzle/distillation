package com.rfizzle.distillation.compat.jei;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The JEI brewing category. Stateless — each {@link BrewingViewerRecipes.Entry} is handed back into
 * {@link #setRecipe} to lay out its input, ingredient, and output. The title is passed explicitly
 * (JEI, unlike EMI, takes a {@code Component}) from the {@code jei.distillation.category.brewing} key.
 */
public class BrewingRecipeCategory extends AbstractRecipeCategory<BrewingViewerRecipes.Entry> {

    public static final RecipeType<BrewingViewerRecipes.Entry> RECIPE_TYPE =
            RecipeType.create(Distillation.MOD_ID, "brewing", BrewingViewerRecipes.Entry.class);

    private static final int SLOT_Y = 5;

    public BrewingRecipeCategory(IGuiHelper guiHelper) {
        super(RECIPE_TYPE,
                Component.translatable("jei.distillation.category.brewing"),
                guiHelper.createDrawableItemStack(new ItemStack(Items.BREWING_STAND)),
                100, 26);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BrewingViewerRecipes.Entry recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, SLOT_Y)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.input()));
        builder.addSlot(RecipeIngredientRole.INPUT, 23, SLOT_Y)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.ingredient()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 79, SLOT_Y)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.output()));
    }
}
