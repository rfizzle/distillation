package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.discovery.BrewProvenances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single brew-completion choke point ({@code design/SPEC.md} §1 Implementation Notes): every
 * potion the stand produces resolves per bottle through here, against the {@link RecipeGraph}.
 * Later features — the murky fallback, batch passes, discovery recording, the
 * {@code DistillationBrewCallback} — hook this seam; nothing else may produce a brewed bottle.
 *
 * <p>{@link #completeBrew} is a faithful reimplementation of vanilla's
 * {@code BrewingStandBlockEntity.doBrew}, with two deliberate differences: resolution consults the
 * graph (so a conversion removed by config genuinely stops brewing), and ingredients marked
 * {@linkplain DistillationBrews#isConsumedWhole consumed whole} suppress the crafting remainder.
 */
public final class BrewSeam {

    private BrewSeam() {
    }

    /** Replaces {@code BrewingStandBlockEntity.doBrew}. Slots 0–2 bottles, 3 ingredient. */
    public static void completeBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        RecipeGraph graph = RecipeGraphs.forLevel(level);
        ItemStack ingredient = items.get(3);
        Map<Integer, ResourceLocation> produced = new LinkedHashMap<>();
        for (int slot = 0; slot < 3; slot++) {
            RecipeGraph.Conversion conversion = graph.matchConversion(ingredient, items.get(slot));
            if (conversion != null) {
                items.set(slot, graph.outputOf(conversion, items.get(slot)));
                produced.put(slot, conversion.id());
            }
        }
        // Matched slots record the conversion that just produced their bottle; discovery reads
        // the record back when a player takes the output. Unmatched slots keep any earlier
        // record — their bottle passed through this cycle unchanged.
        if (level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand) {
            BrewProvenances.recordBrew(stand, produced);
        }

        boolean consumedWhole = DistillationBrews.isConsumedWhole(ingredient);
        ingredient.shrink(1);
        if (!consumedWhole && ingredient.getItem().hasCraftingRemainingItem()) {
            ItemStack remainder = new ItemStack(ingredient.getItem().getCraftingRemainingItem());
            if (ingredient.isEmpty()) {
                ingredient = remainder;
            } else {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
            }
        }
        items.set(3, ingredient);
        level.levelEvent(1035, pos, 0);
    }

    /**
     * Replaces {@code BrewingStandBlockEntity.isBrewable}: a cycle starts when the ingredient is a
     * graph ingredient and at least one bottle has a valid conversion — vanilla's own gate, read
     * through the graph so disabled conversions can't start (and silently waste) a cycle.
     */
    public static boolean isBrewable(RecipeGraph graph, NonNullList<ItemStack> items) {
        ItemStack ingredient = items.get(3);
        if (ingredient.isEmpty() || !graph.isIngredient(ingredient)) {
            return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            ItemStack bottle = items.get(slot);
            if (!bottle.isEmpty() && graph.canBrew(bottle, ingredient)) {
                return true;
            }
        }
        return false;
    }
}
