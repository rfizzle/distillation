// Tier: 1 (pure JUnit — ResourceLocation and the DFU codec API need no bootstrap)
package com.rfizzle.distillation.discovery;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Codec and slot semantics of the stand's brew-provenance record. */
class BrewProvenanceTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("distillation", path);
    }

    @Test
    void codecRoundTrips() {
        BrewProvenance original = new BrewProvenance(Map.of(
                0, id("nether_wart/water"),
                2, id("redstone/distillation/haste")));

        JsonElement encoded = BrewProvenance.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        BrewProvenance decoded = BrewProvenance.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(original.bySlot(), decoded.bySlot());
    }

    @Test
    void encodeIsSlotOrderedForDeterministicSaves() {
        BrewProvenance provenance = new BrewProvenance(Map.of(
                2, id("c"), 0, id("a"), 1, id("b")));
        JsonElement encoded = BrewProvenance.CODEC.encodeStart(JsonOps.INSTANCE, provenance)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));

        var array = encoded.getAsJsonArray();
        assertEquals(3, array.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, array.get(i).getAsJsonObject().get("slot").getAsInt(),
                    "entries serialize sorted by slot");
        }
    }

    @Test
    void malformedSlotsAreDroppedOnLoad() {
        // Slot 8 is past the grown container; slot 7 is a valid batch-row slot (SPEC §3) and stays.
        JsonElement tampered = JsonParser.parseString(
                "[{\"slot\":8,\"recipe\":\"distillation:bogus\"},"
                        + "{\"slot\":7,\"recipe\":\"distillation:batch\"},"
                        + "{\"slot\":1,\"recipe\":\"distillation:ok\"}]");
        BrewProvenance decoded = BrewProvenance.CODEC.parse(JsonOps.INSTANCE, tampered)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));

        assertEquals(Optional.empty(), decoded.forSlot(8), "out-of-range slots from a tampered save are dropped");
        assertEquals(Optional.of(id("batch")), decoded.forSlot(7), "batch-row slots are recordable");
        assertEquals(Optional.of(id("ok")), decoded.forSlot(1));
        assertEquals(2, decoded.bySlot().size());
    }

    @Test
    void entriesMissingFieldsAreDroppedNotFatal() {
        JsonElement legacy = JsonParser.parseString(
                "[{\"recipe\":\"distillation:orphan\"},{\"slot\":2},{\"slot\":0,\"recipe\":\"distillation:ok\"}]");
        BrewProvenance decoded = BrewProvenance.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(message -> new AssertionError(
                        "an entry missing a field must not fail the whole decode: " + message));

        assertEquals(1, decoded.bySlot().size(), "field-less entries are dropped, valid ones kept");
        assertEquals(Optional.of(id("ok")), decoded.forSlot(0));
        assertEquals(Optional.empty(), decoded.forSlot(2));
    }

    @Test
    void withoutRemovesOneSlotAndEmptyReportsEmpty() {
        BrewProvenance provenance = new BrewProvenance(Map.of(0, id("a"), 1, id("b")));
        BrewProvenance without = provenance.without(0);

        assertEquals(Optional.empty(), without.forSlot(0));
        assertEquals(Optional.of(id("b")), without.forSlot(1));
        assertTrue(without.without(1).isEmpty());
        assertTrue(BrewProvenance.EMPTY.isEmpty());
    }
}
