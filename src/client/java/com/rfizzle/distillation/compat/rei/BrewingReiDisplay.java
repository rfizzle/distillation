package com.rfizzle.distillation.compat.rei;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.viewer.BrewingViewerRecipes;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;

import java.util.List;
import java.util.Optional;

/** One brewing conversion as an REI display: input bottle and ingredient in, output bottle out. */
public class BrewingReiDisplay extends BasicDisplay {

    public static final CategoryIdentifier<BrewingReiDisplay> IDENTIFIER =
            CategoryIdentifier.of(Distillation.id("brewing"));

    public BrewingReiDisplay(BrewingViewerRecipes.Entry entry) {
        super(List.of(EntryIngredient.of(EntryStacks.of(entry.input())),
                        EntryIngredient.of(EntryStacks.of(entry.ingredient()))),
                List.of(EntryIngredient.of(EntryStacks.of(entry.output()))),
                Optional.of(entry.id()));
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return IDENTIFIER;
    }
}
