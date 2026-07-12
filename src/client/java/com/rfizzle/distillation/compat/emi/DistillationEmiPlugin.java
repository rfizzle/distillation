package com.rfizzle.distillation.compat.emi;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.Items;

/**
 * EMI plugin ({@code design/SPEC.md} §Compatibility): a single brewing category listing every graph
 * conversion the viewing player has discovered (or all, when {@code recipeViewerShowsUndiscovered}).
 * The category title resolves from the {@code emi.category.distillation.brewing} lang key — EMI takes
 * no title argument. {@link EmiBrewingRefresh} rebuilds this list when discovery or config changes.
 */
public class DistillationEmiPlugin implements EmiPlugin {

    /** The brewing category; its icon and workstation is the brewing stand the recipes live on. */
    public static final EmiRecipeCategory BREWING =
            new EmiRecipeCategory(Distillation.id("brewing"), EmiStack.of(Items.BREWING_STAND));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(BREWING);
        registry.addWorkstation(BREWING, EmiStack.of(Items.BREWING_STAND));
        for (BrewingViewerRecipes.Entry entry : BrewingViewerRecipes.snapshot()) {
            registry.addRecipe(new BrewingEmiRecipe(entry));
        }
    }
}
