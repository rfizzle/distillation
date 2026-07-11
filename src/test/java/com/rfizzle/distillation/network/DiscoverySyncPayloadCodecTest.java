// Tier: 2 (fabric-loader-junit)
package com.rfizzle.distillation.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wire-codec coverage for {@link DiscoverySyncPayload}: round-trip fidelity in both delta shapes,
 * full-buffer consumption, and the decode-side id-count cap that keeps a hostile server from
 * ballooning the client set. No registry bootstrap needed — ids are plain identifiers over
 * {@link FriendlyByteBuf}.
 */
class DiscoverySyncPayloadCodecTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("distillation", path);
    }

    @Test
    void replaceAndDeltaShapesRoundTrip() {
        for (boolean replace : new boolean[]{true, false}) {
            DiscoverySyncPayload original = new DiscoverySyncPayload(replace,
                    List.of(id("nether_wart/water"), id("redstone/distillation/haste")));

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            DiscoverySyncPayload.CODEC.encode(buf, original);
            DiscoverySyncPayload decoded = DiscoverySyncPayload.CODEC.decode(buf);

            assertEquals(original, decoded);
            assertEquals(0, buf.readableBytes(), "codec should consume every byte it wrote");
        }
    }

    @Test
    void emptyIdListRoundTrips() {
        DiscoverySyncPayload original = new DiscoverySyncPayload(true, List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DiscoverySyncPayload.CODEC.encode(buf, original);
        assertEquals(original, DiscoverySyncPayload.CODEC.decode(buf));
    }

    @Test
    void oversizedIdCountIsRejectedAtDecode() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeVarInt(DiscoverySyncPayload.MAX_IDS + 1);

        assertThrows(DecoderException.class, () -> DiscoverySyncPayload.CODEC.decode(buf),
                "an id count past the cap must fail fast before any element is read");
    }
}
