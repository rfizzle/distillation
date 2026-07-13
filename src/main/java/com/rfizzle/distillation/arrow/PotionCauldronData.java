package com.rfizzle.distillation.arrow;

import com.rfizzle.distillation.Distillation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension persistence of which potion tints each charged cauldron ({@code design/SPEC.md}
 * §Tipped arrows). A charged cauldron's remaining capacity is its vanilla water {@code LEVEL}
 * blockstate, which already persists — the only datum without a home is the potion, so this is a
 * bare {@code packed BlockPos → potion id} map. Reads, validation, and the {@link
 * net.minecraft.core.Holder Holder} round-trip go through {@link PotionCauldrons}; this class is
 * the disk layer only.
 *
 * <p>Per {@code mc-persistence}: a non-null {@link DataFixTypes} (a null one NPEs inside the
 * swallowed load and silently drops the state every restart), a {@link ConcurrentHashMap} backing,
 * a {@link #MAX_ENTRIES bounded} map, and serialization sorted by key so saves diff cleanly.
 */
public class PotionCauldronData extends SavedData {

    static final String STORAGE_KEY = "distillation_potion_cauldrons";
    /** A generous cap; realistic worlds hold a handful. Beyond it the lowest-keyed entry is evicted. */
    static final int MAX_ENTRIES = 4096;
    private static final String KEY_CAULDRONS = "cauldrons";
    private static final String KEY_POS = "pos";
    private static final String KEY_POTION = "potion";

    public static final Factory<PotionCauldronData> FACTORY = new Factory<>(
            PotionCauldronData::new, PotionCauldronData::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private final Map<Long, ResourceLocation> charges = new ConcurrentHashMap<>();

    public PotionCauldronData() {
    }

    /** The per-dimension store, created on first write. */
    public static PotionCauldronData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** The per-dimension store if one exists — never creates it (the read and particle-sweep path). */
    public static PotionCauldronData getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(FACTORY, STORAGE_KEY);
    }

    Optional<ResourceLocation> get(long packedPos) {
        return Optional.ofNullable(charges.get(packedPos));
    }

    void put(long packedPos, ResourceLocation potion) {
        ResourceLocation previous = charges.put(packedPos, potion);
        boolean changed = !potion.equals(previous);
        changed |= evictIfOversized();
        if (changed) {
            setDirty();
        }
    }

    void remove(long packedPos) {
        if (charges.remove(packedPos) != null) {
            setDirty();
        }
    }

    /** A snapshot of the charged positions, for the particle sweep. */
    List<Map.Entry<Long, ResourceLocation>> entries() {
        return new ArrayList<>(charges.entrySet());
    }

    boolean isEmpty() {
        return charges.isEmpty();
    }

    /**
     * Bounds the map at {@link #MAX_ENTRIES}. Realistic worlds hold a handful of charged cauldrons,
     * so this only ever fires under abuse or a tampered save; eviction drops the lowest-keyed
     * position (a deterministic, cheap choice — not strictly FIFO, which does not matter at a cap
     * this far above real use).
     */
    private boolean evictIfOversized() {
        boolean evicted = false;
        while (charges.size() > MAX_ENTRIES) {
            long lowest = charges.keySet().stream().min(Long::compareTo).orElseThrow();
            charges.remove(lowest);
            evicted = true;
        }
        return evicted;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_CAULDRONS, writeCharges(charges));
        return tag;
    }

    public static PotionCauldronData load(CompoundTag tag, HolderLookup.Provider registries) {
        PotionCauldronData data = new PotionCauldronData();
        data.charges.putAll(readCharges(tag.getList(KEY_CAULDRONS, Tag.TAG_COMPOUND)));
        return data;
    }

    /** Deterministic serialization: entries sorted by packed position so saves diff cleanly (pure, testable). */
    static ListTag writeCharges(Map<Long, ResourceLocation> charges) {
        ListTag list = new ListTag();
        charges.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putLong(KEY_POS, e.getKey());
                    entry.putString(KEY_POTION, e.getValue().toString());
                    list.add(entry);
                });
        return list;
    }

    /** Reads the charge list, skipping any malformed entry with a warn rather than aborting the load (pure, testable). */
    static Map<Long, ResourceLocation> readCharges(ListTag list) {
        Map<Long, ResourceLocation> charges = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(KEY_POS) || !entry.contains(KEY_POTION)) {
                Distillation.LOGGER.warn("Skipping charged cauldron entry missing pos or potion: {}", entry);
                continue;
            }
            ResourceLocation potion = ResourceLocation.tryParse(entry.getString(KEY_POTION));
            if (potion == null) {
                Distillation.LOGGER.warn("Skipping charged cauldron with unparseable potion id {}",
                        entry.getString(KEY_POTION));
                continue;
            }
            charges.put(entry.getLong(KEY_POS), potion);
        }
        return charges;
    }
}
