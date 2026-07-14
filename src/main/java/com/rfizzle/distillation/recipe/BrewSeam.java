package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.api.DistillationBrewCallback;
import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.BrewProvenances;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.FlaskItem;
import com.rfizzle.distillation.item.MurkyDraughtContents;
import com.rfizzle.distillation.sound.DistillationSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    /**
     * Replaces {@code BrewingStandBlockEntity.doBrew}. Bottom bottles 0–2 and ingredient 3 resolve
     * as vanilla-plus-graph; when the stand's persisted batch flag is set ({@code design/SPEC.md}
     * §3) the batch row 5–7 resolves too — valid, owner-brewable conversions only, never murked —
     * and the ingredient shrinks by {@code batchIngredientCost} instead of one.
     */
    public static void completeBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        RecipeGraph graph = RecipeGraphs.forLevel(level);
        // Only vanilla's serverTick reaches doBrew, so the local (server) config is authoritative.
        DistillationConfig config = Distillation.getConfig();
        boolean murkyEnabled = config.enableMurkyDraughts;
        ItemStack ingredient = items.get(3);
        // Snapshot before the shrink below — the brew callback reports the ingredient consumed.
        ItemStack ingredientSnapshot = ingredient.copy();
        // One seed per pass: bottles sharing an input potion agree on their hint (SPEC §1).
        long hintSeed = MurkyHints.seedFor(pos, level.getGameTime());
        Map<Integer, ResourceLocation> produced = new LinkedHashMap<>();
        Set<Integer> murked = new LinkedHashSet<>();

        BrewingStandBlockEntity stand = level.getBlockEntity(pos) instanceof BrewingStandBlockEntity s ? s : null;
        boolean batch = stand != null && BatchStates.get(stand).brewing();

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

        // Batch row: a committed pass fills a batch bottle only when the ingredient takes it to a
        // conversion the owner may brew (lenient — the pass was paid for at start; see BatchBrew).
        // Everything else in the row is left untouched, never murked. A flask in the row is not a
        // bottle to convert but a fill target (§12): it fills to full with the pass's own output
        // brew, alongside the bottles, once they have resolved.
        if (batch) {
            PotionContents fillBrew = null;
            List<Integer> flaskSlots = null;
            for (int slot = BatchBrew.FIRST_BATCH_SLOT; slot <= BatchBrew.LAST_BATCH_SLOT; slot++) {
                ItemStack bottle = items.get(slot);
                if (bottle.getItem() instanceof FlaskItem) {
                    if (flaskSlots == null) {
                        flaskSlots = new ArrayList<>();
                    }
                    flaskSlots.add(slot);
                    continue;
                }
                RecipeGraph.Conversion conversion =
                        BatchBrew.batchConversion(stand, level, ingredient, bottle, graph, config, true);
                if (conversion != null) {
                    ItemStack output = graph.outputOf(conversion, bottle);
                    items.set(slot, output);
                    produced.put(slot, conversion.id());
                    if (fillBrew == null) {
                        fillBrew = output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                    }
                }
            }
            // The pass's fill brew is the output the row produced; a flask empty or already holding it
            // fills to its three-dose cap (a mismatched or full flask is left untouched, never murked).
            // The owner already discovered that brew — it is what the sibling bottles just resolved to.
            if (flaskSlots != null && config.enableFlask
                    && fillBrew != null && !fillBrew.equals(PotionContents.EMPTY)) {
                for (int slot : flaskSlots) {
                    ItemStack flask = items.get(slot);
                    if (FlaskItem.canFill(flask, fillBrew)) {
                        FlaskItem.fillToFull(flask, fillBrew);
                    }
                }
            }
        }

        // Matched slots record the conversion that just produced their bottle; discovery reads
        // the record back when a player takes the output. Murked slots clear any earlier record —
        // a Murky Draught is nobody's brewed output. Untouched slots keep theirs.
        if (stand != null) {
            BrewProvenances.recordBrew(stand, produced, murked);
        }
        if (!murked.isEmpty()) {
            level.playSound(null, pos, DistillationSounds.MURKY_FIZZLE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        int cost = batch ? config.batchIngredientCost : 1;
        items.set(3, consumeIngredient(level, pos, ingredient, cost));

        if (batch) {
            BatchStates.setBrewing(stand, false); // the committed pass is done; the rig may eject next tick
        }
        level.levelEvent(1035, pos, 0);

        // Public API observation seam (SPEC §Public API): one immutable snapshot of what the cycle
        // produced. Only vanilla's server tick reaches here, so the level is always a ServerLevel.
        if (level instanceof ServerLevel serverLevel) {
            UUID batchOwner = batch && stand != null ? BatchStates.owner(stand).orElse(null) : null;
            DistillationBrewCallback.EVENT.invoker().onBrew(
                    serverLevel, pos, ingredientSnapshot, brewResults(items, produced, murked), batchOwner, batch);
        }
    }

    /**
     * An immutable snapshot of the bottles this cycle actually produced — only the slots it converted
     * ({@code produced}) or turned to a Murky Draught ({@code murked}), as copies, so a
     * {@code DistillationBrewCallback} listener sees the pass's real output (never a leftover,
     * untouched bottle) and can never mutate the stand's live inventory.
     */
    private static List<ItemStack> brewResults(NonNullList<ItemStack> items,
                                               Map<Integer, ResourceLocation> produced, Set<Integer> murked) {
        List<ItemStack> results = new ArrayList<>();
        for (int slot : produced.keySet()) {
            results.add(items.get(slot).copy());
        }
        for (int slot : murked) {
            results.add(items.get(slot).copy());
        }
        return List.copyOf(results);
    }

    /**
     * Shrinks the ingredient by {@code count} and settles its crafting remainder as vanilla does,
     * scaled to the batch cost: an ingredient {@linkplain DistillationBrews#isConsumedWhole consumed
     * whole} leaves nothing, otherwise each consumed unit yields a remainder — the first refilling an
     * emptied slot, the rest dropped beside the stand.
     */
    private static ItemStack consumeIngredient(Level level, BlockPos pos, ItemStack ingredient, int count) {
        Item item = ingredient.getItem();
        boolean consumedWhole = DistillationBrews.isConsumedWhole(ingredient);
        int consumed = Math.min(count, ingredient.getCount());
        ingredient.shrink(count);
        if (consumedWhole || !item.hasCraftingRemainingItem()) {
            return ingredient;
        }
        Item remainderItem = item.getCraftingRemainingItem();
        for (int i = 0; i < consumed; i++) {
            ItemStack remainder = new ItemStack(remainderItem);
            if (ingredient.isEmpty()) {
                ingredient = remainder;
            } else {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
            }
        }
        return ingredient;
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
        Optional<ResourceLocation> hint = MurkyHints.select(graph.conversionsFor(bottle), hintSeed,
                        conversion -> conversion instanceof RecipeGraph.PotionConversion)
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
