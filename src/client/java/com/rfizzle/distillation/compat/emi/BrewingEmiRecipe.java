package com.rfizzle.distillation.compat.emi;

import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

/**
 * One brewing conversion as EMI draws it: the input bottle and ingredient on the left, the output
 * bottle on the right. The recipe id is the graph conversion's own stable id, unique per row.
 */
public class BrewingEmiRecipe extends BasicEmiRecipe {

    private static final int SLOT_Y = 4;

    private final BrewingViewerRecipes.Entry entry;

    public BrewingEmiRecipe(BrewingViewerRecipes.Entry entry) {
        super(DistillationEmiPlugin.BREWING, entry.id(), 100, 26);
        this.entry = entry;
        this.inputs.add(EmiStack.of(entry.input()));
        this.inputs.add(EmiStack.of(entry.ingredient()));
        this.outputs.add(EmiStack.of(entry.output()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(EmiStack.of(entry.input()), 0, SLOT_Y);
        widgets.addSlot(EmiStack.of(entry.ingredient()), 22, SLOT_Y);
        widgets.addTexture(dev.emi.emi.api.render.EmiTexture.EMPTY_ARROW, 48, SLOT_Y + 1);
        widgets.addSlot(EmiStack.of(entry.output()), 78, SLOT_Y).recipeContext(this);
    }
}
