// Tier: 2 (fabric-loader-junit + Bootstrap — UUIDUtil.CODEC and NbtOps need the game bootstrapped)
package com.rfizzle.distillation.batch;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch attachment's codec survives the save round-trip it will take on a stand's block entity
 * ({@code design/SPEC.md} §3 Ownership — "the owner survives unload"): owner and brewing flag both
 * persist, and a legacy/hand-edited tag missing the brewing flag decodes to a safe default rather
 * than failing the load.
 */
class BatchStateCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ownerAndBrewingRoundTrip() {
        UUID owner = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        BatchState decoded = roundTrip(new BatchState(Optional.of(owner), true));
        assertEquals(Optional.of(owner), decoded.owner());
        assertTrue(decoded.brewing());
    }

    @Test
    void emptyStateRoundTrips() {
        BatchState decoded = roundTrip(BatchState.EMPTY);
        assertTrue(decoded.owner().isEmpty());
        assertFalse(decoded.brewing());
        assertTrue(decoded.isEmpty());
    }

    @Test
    void aTagMissingBrewingDecodesToFalse() {
        CompoundTag tag = new CompoundTag(); // no owner, no brewing
        BatchState decoded = BatchState.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        assertTrue(decoded.owner().isEmpty());
        assertFalse(decoded.brewing());
    }

    private static BatchState roundTrip(BatchState state) {
        Codec<BatchState> codec = BatchState.CODEC;
        Tag encoded = codec.encodeStart(NbtOps.INSTANCE, state).result().orElseThrow();
        return codec.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
    }
}
