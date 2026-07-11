package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.discovery.BrewProvenances;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.MurkyDraughtContents;
import com.rfizzle.distillation.sound.DistillationSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single brew-completion choke point ({@code design/SPEC.md} §1 Implementation Notes): every
 * potion the stand produces resolves per bottle through here, against the {@link RecipeGraph} —
 * valid pairs to their output, invalid-but-receptive pairs to a Murky Draught when
 * {@code enableMurkyDraughts} is on. Later features — batch passes, the
 * {@code DistillationBrewCallback} — hook this seam; nothing else may produce a brewed bottle.
 *
 * <p>{@link #completeBrew} is a faithful reimplementation of vanilla's
 * {@code BrewingStandBlockEntity.doBrew}, with three deliberate differences: resolution consults
 * the graph (so a conversion removed by config genuinely stops brewing), invalid pairs bottle
 * Murky Draughts under the config above (off, they pass through exactly as vanilla), and
 * ingredients marked {@linkplain DistillationBrews#isConsumedWhole consumed whole} suppress the
 * crafting remainder.
 */
public final class BrewSeam {

    private BrewSeam() {
    }

    /** Replaces {@code BrewingStandBlockEntity.doBrew}. Slots 0–2 bottles, 3 ingredient. */
    public static void completeBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        RecipeGraph graph = RecipeGraphs.forLevel(level);
        // Only vanilla's serverTick reaches doBrew, so the local (server) config is authoritative.
        boolean murkyEnabled = Distillation.getConfig().enableMurkyDraughts;
        ItemStack ingredient = items.get(3);
        // One seed per pass: bottles sharing an input potion agree on their hint (SPEC §1).
        long hintSeed = MurkyHints.seedFor(pos, level.getGameTime());
        Map<Integer, ResourceLocation> produced = new LinkedHashMap<>();
        Set<Integer> murked = new LinkedHashSet<>();
        for (int slot = 0; slot < 3; slot++) {
            ItemStack bottle = items.get(slot);
            RecipeGraph.Conversion conversion = graph.matchConversion(ingredient, bottle);
            if (conversion != null) {
                items.set(slot, graph.outputOf(conversion, bottle));
                produced.put(slot, conversion.id());
            } else if (murkyEnabled && graph.isReceptive(bottle)) {
                ItemStack draught = murkyDraught(graph, bottle, hintSeed);
                if (draught != null) {
                    items.set(slot, draught);
                    murked.add(slot);
                }
            }
        }
        // Matched slots record the conversion that just produced their bottle; discovery reads
        // the record back when a player takes the output. Murked slots clear any earlier record —
        // a Murky Draught is nobody's brewed output. Untouched slots keep theirs.
        if (level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand) {
            BrewProvenances.recordBrew(stand, produced, murked);
        }
        if (!murked.isEmpty()) {
            level.playSound(null, pos, DistillationSounds.MURKY_FIZZLE, SoundSource.BLOCKS, 1.0F, 1.0F);
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
     * The Murky Draught a failed bottle becomes: input potion recorded, hint picked seeded-uniform
     * from the conversions that would have taken (absent when nothing brews onward — the hintless
     * draught). {@code null} — pass the bottle through instead — only for a potion holder with no
     * registry key, whose input a draught could never faithfully record.
     */
    @Nullable
    private static ItemStack murkyDraught(RecipeGraph graph, ItemStack bottle, long hintSeed) {
        Optional<ResourceLocation> inputPotion = bottle
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location);
        if (inputPotion.isEmpty()) {
            return null;
        }
        Optional<ResourceLocation> hint = MurkyHints.select(graph.conversionsFor(bottle), hintSeed)
                .map(conversion -> BuiltInRegistries.ITEM.getKey(conversion.ingredient()));
        ItemStack draught = new ItemStack(DistillationItems.MURKY_DRAUGHT);
        draught.set(DistillationItems.MURKY_DRAUGHT_CONTENTS, new MurkyDraughtContents(inputPotion.get(), hint));
        return draught;
    }

    /**
     * Replaces {@code BrewingStandBlockEntity.isBrewable}. With Murky Draughts on, a cycle starts
     * (and keeps running — vanilla re-checks every tick) when the ingredient is a graph ingredient
     * and at least one bottle is {@linkplain RecipeGraph#isReceptive receptive}, valid pair or not
     * — the failed pass is a real pass. Off, it is vanilla's own gate read through the graph: at
     * least one bottle must hold a valid conversion, so invalid pairs never start (and silently
     * waste) a cycle.
     */
    public static boolean isBrewable(RecipeGraph graph, NonNullList<ItemStack> items, boolean murkyDraughtsEnabled) {
        ItemStack ingredient = items.get(3);
        if (ingredient.isEmpty() || !graph.isIngredient(ingredient)) {
            return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            ItemStack bottle = items.get(slot);
            if (bottle.isEmpty()) {
                continue;
            }
            if (murkyDraughtsEnabled ? graph.isReceptive(bottle) : graph.canBrew(bottle, ingredient)) {
                return true;
            }
        }
        return false;
    }
}
