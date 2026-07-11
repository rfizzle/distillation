package com.rfizzle.distillation.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's permanent recipe-discovery set ({@code design/SPEC.md} §1): every recipe id the
 * player has ever discovered, in discovery order — insertion order is semantic, it is the order
 * the recipes page lists entries in. Ids that no longer resolve against the current recipe graph
 * are retained here but hidden from counts ({@link #discoveredCount}), so a recipe removed by a
 * datapack or mod change reappears with its discovery intact when the recipe returns.
 *
 * <p>Mutations happen only through {@link DiscoveryManager} — the write choke point that also
 * pushes the client sync. This class itself is the pure core: no Minecraft state beyond
 * {@link ResourceLocation}, so the set semantics unit-test at tier 1.
 */
public final class DiscoveryData {

    /**
     * Hard bound on stored entries — a hostile-save guard, not a gameplay limit. Real graphs hold
     * a few hundred ids; reaching this cap requires thousands of distinct recipe ids accumulated
     * across datapack churn. Beyond it the oldest entries are evicted FIFO.
     */
    public static final int MAX_ENTRIES = 16384;

    public static final Codec<DiscoveryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("recipes", List.of())
                    .forGetter(DiscoveryData::orderedIds)
    ).apply(instance, DiscoveryData::new));

    // Insertion-ordered: discovery order, preserved through the codec round trip.
    private final LinkedHashSet<ResourceLocation> recipes;

    public DiscoveryData() {
        this.recipes = new LinkedHashSet<>();
    }

    private DiscoveryData(List<ResourceLocation> recipes) {
        this.recipes = new LinkedHashSet<>(recipes); // dedupe, keeping first occurrence
        enforceCap(this.recipes);
    }

    /** Records a discovery; {@code true} only the first time this id is added. */
    boolean add(ResourceLocation recipeId) {
        boolean added = recipes.add(recipeId);
        if (added) {
            enforceCap(recipes);
        }
        return added;
    }

    /** Adds every id, preserving discovery order for the new ones; returns how many were new. */
    int addAll(Collection<ResourceLocation> recipeIds) {
        int before = recipes.size();
        recipes.addAll(recipeIds);
        enforceCap(recipes);
        return recipes.size() - before;
    }

    /** Removes a discovery ({@code /distillation forget}); {@code true} when it was present. */
    boolean remove(ResourceLocation recipeId) {
        return recipes.remove(recipeId);
    }

    /** Removes every discovery ({@code /distillation forget all}); returns how many were stored. */
    int clear() {
        int removed = recipes.size();
        recipes.clear();
        return removed;
    }

    public boolean contains(ResourceLocation recipeId) {
        return recipes.contains(recipeId);
    }

    /** Every stored id in discovery order — including entries stale against the current graph. */
    public List<ResourceLocation> orderedIds() {
        return List.copyOf(recipes);
    }

    /** Stored ids that resolve against the given graph ids — the visible discovery count. */
    public int discoveredCount(Set<ResourceLocation> graphIds) {
        int count = 0;
        for (ResourceLocation id : recipes) {
            if (graphIds.contains(id)) {
                count++;
            }
        }
        return count;
    }

    /** The most recent graph-resolvable discoveries, newest first, at most {@code limit}. */
    public List<ResourceLocation> latestDiscovered(Set<ResourceLocation> graphIds, int limit) {
        List<ResourceLocation> visible = new ArrayList<>();
        for (ResourceLocation id : recipes) {
            if (graphIds.contains(id)) {
                visible.add(id);
            }
        }
        List<ResourceLocation> latest = new ArrayList<>();
        for (int i = visible.size() - 1; i >= 0 && latest.size() < limit; i--) {
            latest.add(visible.get(i));
        }
        return latest;
    }

    private static void enforceCap(LinkedHashSet<ResourceLocation> recipes) {
        Iterator<ResourceLocation> oldest = recipes.iterator();
        while (recipes.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
