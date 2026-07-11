// Tier: 1 (pure JUnit — ResourceLocation and the DFU codec API need no bootstrap)
package com.rfizzle.distillation.discovery;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The discovery-set semantics of {@code design/SPEC.md} §1: idempotent re-discovery, discovery
 * order as insertion order, stale ids hidden from counts but retained in storage (reappearing when
 * the recipe returns), forget as the only removal, and the hostile-save cap.
 */
class DiscoveryDataTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("distillation", path);
    }

    @Test
    void addIsIdempotent_trueOnlyFirstTime() {
        DiscoveryData data = new DiscoveryData();
        assertTrue(data.add(id("nether_wart/water")));
        assertFalse(data.add(id("nether_wart/water")), "re-discovery must be silently idempotent");
        assertEquals(1, data.orderedIds().size());
    }

    @Test
    void orderedIdsPreserveDiscoveryOrder() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("c"));
        data.add(id("a"));
        data.add(id("b"));
        assertEquals(List.of(id("c"), id("a"), id("b")), data.orderedIds());
    }

    @Test
    void staleIdsHideFromCountButStayStored_andReappear() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("kept"));
        data.add(id("removed_by_datapack"));
        data.add(id("kept_too"));

        Set<ResourceLocation> shrunkGraph = Set.of(id("kept"), id("kept_too"));
        assertEquals(2, data.discoveredCount(shrunkGraph), "stale ids are hidden from the count");
        assertEquals(3, data.orderedIds().size(), "stale ids are retained in storage");
        assertTrue(data.contains(id("removed_by_datapack")));

        Set<ResourceLocation> restoredGraph = Set.of(id("kept"), id("kept_too"), id("removed_by_datapack"));
        assertEquals(3, data.discoveredCount(restoredGraph), "a returning recipe reappears discovered");
    }

    @Test
    void latestDiscovered_newestFirst_skipsStale_capsAtLimit() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("first"));
        data.add(id("stale"));
        data.add(id("second"));
        data.add(id("third"));

        Set<ResourceLocation> graph = Set.of(id("first"), id("second"), id("third"));
        assertEquals(List.of(id("third"), id("second")), data.latestDiscovered(graph, 2));
        assertEquals(List.of(id("third"), id("second"), id("first")), data.latestDiscovered(graph, 5));
    }

    @Test
    void forgetRemovesOneOrAll() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("a"));
        data.add(id("b"));

        assertTrue(data.remove(id("a")));
        assertFalse(data.remove(id("a")), "forgetting an absent id reports nothing removed");
        assertTrue(data.add(id("a")), "a forgotten id can be re-discovered");
        assertEquals(2, data.clear());
        assertEquals(0, data.orderedIds().size());
    }

    @Test
    void addAllCountsOnlyNewIds() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("a"));
        assertEquals(2, data.addAll(List.of(id("a"), id("b"), id("c"))));
    }

    @Test
    void capEvictsOldestBeyondMaxEntries() {
        DiscoveryData data = new DiscoveryData();
        for (int i = 0; i < DiscoveryData.MAX_ENTRIES + 3; i++) {
            data.add(id("r" + i));
        }
        assertEquals(DiscoveryData.MAX_ENTRIES, data.orderedIds().size());
        assertFalse(data.contains(id("r0")), "the oldest entries evict first");
        assertTrue(data.contains(id("r" + (DiscoveryData.MAX_ENTRIES + 2))));
    }

    @Test
    void codecRoundTripPreservesOrder() {
        DiscoveryData data = new DiscoveryData();
        data.add(id("z"));
        data.add(id("m"));
        data.add(id("a"));

        JsonElement encoded = DiscoveryData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        DiscoveryData decoded = DiscoveryData.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(data.orderedIds(), decoded.orderedIds());
    }

    @Test
    void codecToleratesMissingField() {
        DiscoveryData decoded = DiscoveryData.CODEC.parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString("{}"))
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(0, decoded.orderedIds().size(), "a save written before the field existed loads empty");
    }
}
