package com.rfizzle.distillation.network;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.discovery.DiscoveryData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S2C push of the owner's discovery set ({@code design/SPEC.md} §1 Sync) — an id-list delta.
 * {@code replace=true} carries the full stored set (join, {@code forget}, {@code discover all});
 * {@code replace=false} appends newly discovered ids (the common single-discovery case). The
 * client keeps the raw stored set and intersects with its own graph at read time, so stale ids
 * hide and reappear client-side exactly as they do on the server.
 */
public record DiscoverySyncPayload(boolean replace, List<ResourceLocation> recipeIds)
        implements CustomPacketPayload {

    // Decode-side bound (a hostile server must not OOM the client); matches the storage cap,
    // which the server-side set can never exceed.
    public static final int MAX_IDS = DiscoveryData.MAX_ENTRIES;

    public static final Type<DiscoverySyncPayload> TYPE =
            new Type<>(Distillation.id("discovery_sync"));

    public static final StreamCodec<FriendlyByteBuf, DiscoverySyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DiscoverySyncPayload::replace,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_IDS)),
                    DiscoverySyncPayload::recipeIds,
                    DiscoverySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
