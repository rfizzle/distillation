package com.rfizzle.distillation.network;

import com.rfizzle.distillation.Distillation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload carrying the server's authoritative gameplay config as a compact JSON string
 * ({@link com.rfizzle.distillation.config.DistillationConfig#toSyncJson()}). Sent on player join
 * (and re-broadcast on config reload) so a connected client plays under the server's rules instead
 * of its own local {@code distillation.json}.
 *
 * <p>The client stores this as the server-authoritative copy and reads it first, falling back to
 * its local config only in true singleplayer or before the join payload arrives. The client still
 * reads its own local {@code client} preferences — those are intentionally excluded from the
 * synced view (see {@code toSyncJson()}).
 */
public record ConfigSyncPayload(String configJson) implements CustomPacketPayload {

    // Cap the serialized config JSON. writeUtf/readUtf enforce a char limit; the cap sits well
    // above the realistic config size (the default is ~0.5k chars) while bounding a hostile
    // server's payload below writeUtf's 32767 hard limit. If a future config addition legitimately
    // exceeds this, the codec throws EncoderException — a deliberate fail-fast signal to bump the
    // cap or switch to per-field encoding.
    public static final int MAX_CONFIG_JSON_CHARS = 16384;

    public static final Type<ConfigSyncPayload> TYPE =
            new Type<>(Distillation.id("config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.configJson, MAX_CONFIG_JSON_CHARS),
                    buf -> new ConfigSyncPayload(buf.readUtf(MAX_CONFIG_JSON_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
