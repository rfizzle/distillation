package com.rfizzle.distillation.data;

import com.rfizzle.distillation.item.DistillationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * Distillation's item models — the two that are plain flat sprites of a registered item.
 *
 * <p>The mod registers no blocks at all (it takes over vanilla's brewing stand through mixins
 * rather than shipping one of its own), so {@link #generateBlockStateModels} has nothing to emit.
 * The remaining five model files stay hand-authored under {@code src/main/resources}; the reasons
 * are enumerated on {@link DistillationDataGenerator}.
 */
public class DistillationModelProvider extends FabricModelProvider {

    public DistillationModelProvider(FabricDataOutput output) {
        super(output);
    }

    /**
     * Distillation ships no blocks, so there are no blockstates or block models to generate. The
     * override is required by {@link FabricModelProvider}; leaving it empty is the honest answer,
     * and an empty body here still produces no output file rather than an empty one.
     */
    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        // The Flask is absent on purpose: its model carries an `overrides` block keyed on the
        // `distillation:filled` item property, which no vanilla ModelTemplate can express.
        generators.generateFlatItem(DistillationItems.MURKY_DRAUGHT, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(DistillationItems.RECIPE_NOTE, ModelTemplates.FLAT_ITEM);
    }
}
