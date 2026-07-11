package com.rfizzle.distillation.client.net;

import com.rfizzle.distillation.client.config.ClientDistillationConfig;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.network.ConfigSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * S2C receivers. The config sync payload stores the server's authoritative gameplay config into
 * {@link ClientDistillationConfig} so gameplay-affecting client readers prefer it over the local
 * file.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE,
                (payload, context) -> {
                    // Decode off the client thread — GSON parsing is pure and has no client-state
                    // dependency — then publish the immutable result on the client thread.
                    DistillationConfig synced = DistillationConfig.fromJson(payload.configJson());
                    context.client().execute(() -> ClientDistillationConfig.setServerConfig(synced));
                });
    }
}
