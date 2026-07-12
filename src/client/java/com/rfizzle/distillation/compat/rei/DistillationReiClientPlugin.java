package com.rfizzle.distillation.compat.rei;

import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * REI plugin ({@code design/SPEC.md} §Compatibility): the brewing category and its displays, one per
 * discovered graph conversion. The workstation entry is the brewing stand the recipes live on.
 */
public class DistillationReiClientPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new BrewingReiDisplayCategory());
        registry.addWorkstations(BrewingReiDisplay.IDENTIFIER,
                EntryIngredient.of(EntryStacks.of(new ItemStack(Items.BREWING_STAND))));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (BrewingViewerRecipes.Entry entry : BrewingViewerRecipes.snapshot()) {
            registry.add(new BrewingReiDisplay(entry));
        }
    }
}
