package com.rfizzle.distillation.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.distillation.Distillation;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which conversion produced the bottle currently sitting in each of a brewing stand's bottle
 * slots — written by the brew seam when a cycle completes, consumed (and cleared) by the
 * output-slot extraction hook that records discovery. Persisted on the stand's block entity so a
 * brew still teaches after a relog or chunk unload between brewing and taking.
 *
 * <p>Immutable; the stand's attachment is swapped whole through {@link BrewProvenances}.
 */
public record BrewProvenance(Map<Integer, ResourceLocation> bySlot) {

    /** Bottle slots are 0–2; anything else in a save is malformed and dropped on load. */
    private static final int BOTTLE_SLOTS = 3;

    public static final BrewProvenance EMPTY = new BrewProvenance(Map.of());

    private record Entry(int slot, ResourceLocation recipeId) {
        // optionalFieldOf sentinels: an entry missing a field (legacy or hand-edited save)
        // decodes to these and is dropped by the filter below, instead of one bad entry failing
        // the whole stand's provenance.
        static final int SLOT_UNSET = -1;
        static final ResourceLocation RECIPE_UNSET = Distillation.id("unset");

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("slot", SLOT_UNSET).forGetter(Entry::slot),
                ResourceLocation.CODEC.optionalFieldOf("recipe", RECIPE_UNSET).forGetter(Entry::recipeId)
        ).apply(instance, Entry::new));
    }

    public static final Codec<BrewProvenance> CODEC = Entry.CODEC.listOf()
            .xmap(entries -> {
                Map<Integer, ResourceLocation> bySlot = new LinkedHashMap<>();
                for (Entry entry : entries) {
                    if (entry.slot() >= 0 && entry.slot() < BOTTLE_SLOTS
                            && !Entry.RECIPE_UNSET.equals(entry.recipeId())) {
                        bySlot.put(entry.slot(), entry.recipeId());
                    }
                }
                return new BrewProvenance(bySlot);
            }, provenance -> provenance.bySlot().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // deterministic serialization
                    .map(e -> new Entry(e.getKey(), e.getValue()))
                    .toList());

    public BrewProvenance {
        bySlot = Map.copyOf(bySlot);
    }

    public Optional<ResourceLocation> forSlot(int slot) {
        return Optional.ofNullable(bySlot.get(slot));
    }

    /** A copy with this slot's entry removed. */
    public BrewProvenance without(int slot) {
        if (!bySlot.containsKey(slot)) {
            return this;
        }
        Map<Integer, ResourceLocation> remaining = new LinkedHashMap<>(bySlot);
        remaining.remove(slot);
        return new BrewProvenance(remaining);
    }

    public boolean isEmpty() {
        return bySlot.isEmpty();
    }
}
