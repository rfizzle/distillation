package com.rfizzle.distillation.compat.jei;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * JEI plugin ({@code design/SPEC.md} §Compatibility): the brewing category listing the viewing
 * player's discovered graph conversions. JEI's runtime API is the cleanest of the three for the
 * reactive refresh — after the discovery or config set changes, {@link #refresh()} hides the last
 * list it added and re-adds the fresh snapshot (idempotent hide-then-add). The {@code @JeiPlugin}
 * annotation is load-bearing on other loaders; on Fabric the {@code jei_mod_plugin} entrypoint is
 * what discovers this class.
 */
@JeiPlugin
public class DistillationJeiPlugin implements IModPlugin {

    private static volatile IRecipeManager recipeManager;
    private static volatile List<BrewingViewerRecipes.Entry> published = List.of();
    private static volatile boolean refreshFailed = false;

    @Override
    public ResourceLocation getPluginUid() {
        return Distillation.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new BrewingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BrewingViewerRecipes.Entry> rows = BrewingViewerRecipes.snapshot();
        registration.addRecipes(BrewingRecipeCategory.RECIPE_TYPE, rows);
        published = rows;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.BREWING_STAND), BrewingRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        recipeManager = jeiRuntime.getRecipeManager();
        JeiBrewingRefresh.bind(DistillationJeiPlugin::refresh);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBrewingRefresh.unbind();
        recipeManager = null;
        published = List.of();
    }

    /** Idempotent: hide the exact list last added (JEI's hidden set is identity-keyed), then add fresh. */
    private static void refresh() {
        IRecipeManager manager = recipeManager;
        if (manager == null || refreshFailed) {
            return;
        }
        try {
            if (!published.isEmpty()) {
                manager.hideRecipes(BrewingRecipeCategory.RECIPE_TYPE, published);
            }
            List<BrewingViewerRecipes.Entry> current = BrewingViewerRecipes.snapshot();
            manager.addRecipes(BrewingRecipeCategory.RECIPE_TYPE, current);
            published = current;
        } catch (RuntimeException e) {
            refreshFailed = true;
            Distillation.LOGGER.warn("JEI brewing-category refresh failed; it refreshes on rejoin instead", e);
        }
    }
}
