// Tier: 1 (pure JUnit — plain NBT + ResourceLocation, no MC bootstrap)
package com.rfizzle.distillation.arrow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charged-cauldron persistence format ({@code mc-persistence}): a {@code packed BlockPos →
 * potion id} map round-trips through NBT, serializes sorted by position so saves diff cleanly, and
 * drops malformed entries rather than aborting the whole load.
 */
class PotionCauldronDataTest {

    @Test
    void roundTripsChargesThroughNbt() {
        Map<Long, ResourceLocation> charges = new LinkedHashMap<>();
        charges.put(42L, ResourceLocation.parse("minecraft:swiftness"));
        charges.put(7L, ResourceLocation.parse("minecraft:strength"));

        Map<Long, ResourceLocation> read = PotionCauldronData.readCharges(PotionCauldronData.writeCharges(charges));

        assertEquals(charges, read);
    }

    @Test
    void serializesSortedByPackedPosition() {
        Map<Long, ResourceLocation> charges = new LinkedHashMap<>();
        charges.put(42L, ResourceLocation.parse("minecraft:swiftness"));
        charges.put(7L, ResourceLocation.parse("minecraft:strength"));

        ListTag tag = PotionCauldronData.writeCharges(charges);

        assertEquals(7L, tag.getCompound(0).getLong("pos"), "lowest packed pos serializes first");
        assertEquals(42L, tag.getCompound(1).getLong("pos"));
    }

    @Test
    void boundsTheMapAtTheCapEvictingTheLowestKey() {
        PotionCauldronData data = new PotionCauldronData();
        ResourceLocation potion = ResourceLocation.parse("minecraft:swiftness");
        // Fill one past the cap; the lowest-keyed entry (0) is evicted, size holds at the cap.
        for (long pos = 0; pos <= PotionCauldronData.MAX_ENTRIES; pos++) {
            data.put(pos, potion);
        }

        assertEquals(PotionCauldronData.MAX_ENTRIES, data.entries().size(), "the map is bounded at the cap");
        assertTrue(data.get(0L).isEmpty(), "the lowest-keyed entry is evicted");
        assertTrue(data.get((long) PotionCauldronData.MAX_ENTRIES).isPresent(), "the newest entry survives");
    }

    @Test
    void skipsMalformedEntriesOnRead() {
        ListTag tag = new ListTag();
        CompoundTag unparseable = new CompoundTag();
        unparseable.putLong("pos", 1L);
        unparseable.putString("potion", "not a valid id!!!");
        tag.add(unparseable);
        CompoundTag missingPotion = new CompoundTag();
        missingPotion.putLong("pos", 5L);
        tag.add(missingPotion);
        CompoundTag good = new CompoundTag();
        good.putLong("pos", 2L);
        good.putString("potion", "minecraft:luck");
        tag.add(good);

        Map<Long, ResourceLocation> read = PotionCauldronData.readCharges(tag);

        assertEquals(1, read.size(), "only the well-formed entry survives");
        assertEquals(ResourceLocation.parse("minecraft:luck"), read.get(2L));
    }
}
