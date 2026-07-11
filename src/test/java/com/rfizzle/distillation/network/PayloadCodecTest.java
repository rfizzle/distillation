// Tier: 2 (fabric-loader-junit)
package com.rfizzle.distillation.network;

import com.rfizzle.distillation.config.DistillationConfig;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-codec coverage for {@link ConfigSyncPayload}: round-trip fidelity, full-buffer consumption,
 * the hostile-payload size cap, and the end-to-end sync shape (a config serialized with
 * {@code toSyncJson()} decodes on the other side with the server's values and no client block).
 * No registry bootstrap needed — the payload is a plain UTF string over {@link FriendlyByteBuf}.
 */
class PayloadCodecTest {

    @Test
    void configSyncPayload_roundTrips() {
        ConfigSyncPayload original = new ConfigSyncPayload(new DistillationConfig().toSyncJson());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ConfigSyncPayload.CODEC.encode(buf, original);
        ConfigSyncPayload decoded = ConfigSyncPayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes(), "codec should consume every byte it wrote");
    }

    @Test
    void oversizedConfigJsonIsRejectedAtEncode() {
        String oversized = "x".repeat(ConfigSyncPayload.MAX_CONFIG_JSON_CHARS + 1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        assertThrows(EncoderException.class,
                () -> ConfigSyncPayload.CODEC.encode(buf, new ConfigSyncPayload(oversized)),
                "a payload past the char cap must fail fast at encode");
    }

    @Test
    void syncedConfigCarriesServerValuesAndNoClientBlock() {
        DistillationConfig serverSide = new DistillationConfig();
        serverSide.enableBatchBrewing = false;
        serverSide.splashDurationFactor = 0.6f;
        serverSide.client.showVaporHints = false;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ConfigSyncPayload.CODEC.encode(buf, new ConfigSyncPayload(serverSide.toSyncJson()));
        DistillationConfig clientSide = DistillationConfig.fromJson(
                ConfigSyncPayload.CODEC.decode(buf).configJson());

        assertFalse(clientSide.enableBatchBrewing, "server gameplay values arrive as sent");
        assertEquals(0.6f, clientSide.splashDurationFactor, "server gameplay values arrive as sent");
        assertTrue(clientSide.client.showVaporHints,
                "the client block is excluded from the wire form, so the receiver null-heals it to defaults");
    }
}
