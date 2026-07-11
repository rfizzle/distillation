package com.rfizzle.distillation.network;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the mod's S2C payloads and the server-side lifecycle hooks that keep clients in sync.
 * Called from {@link Distillation#onInitialize} during mod load so the types are resolvable before
 * any play-phase traffic starts.
 */
public final class DistillationNetworking {

    private DistillationNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
    }

    /**
     * Registers the join hook that sends each connecting player the server's gameplay config, so
     * both sides agree on the rules from the first tick. Must be called during
     * {@link Distillation#onInitialize} after {@link #registerPayloads()}.
     */
    public static void registerLifecycleHandlers() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendConfig(handler.player));
    }

    /**
     * Sends the server-authoritative gameplay config to a single player. The {@code canSend} guard
     * skips a client (e.g. vanilla) that has not registered the receiver.
     */
    public static void sendConfig(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, buildConfigPayload());
        }
    }

    /**
     * Re-broadcasts the current server config to every connected player, so a live config change
     * reaches connected clients without a reconnect. The future {@code /distillation reload}
     * command wires here via {@link Distillation#reloadConfig(MinecraftServer)}.
     */
    public static void syncConfigToAll(MinecraftServer server) {
        ConfigSyncPayload payload = buildConfigPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static ConfigSyncPayload buildConfigPayload() {
        return new ConfigSyncPayload(Distillation.getConfig().toSyncJson());
    }
}
