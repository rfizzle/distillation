package com.rfizzle.distillation.client.net;

import com.rfizzle.distillation.client.config.ClientDistillationConfig;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.compat.viewer.BrewingViewerRefresh;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.network.ConfigSyncPayload;
import com.rfizzle.distillation.network.DiscoverySyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * S2C receivers. The config sync payload stores the server's authoritative gameplay config into
 * {@link ClientDistillationConfig} so gameplay-affecting client readers prefer it over the local
 * file; the discovery sync payload keeps {@link ClientDiscoveryState} mirroring the player's
 * server-side discovery set.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE,
                (payload, context) -> {
                    // Parse before scheduling the publish — fromJson is pure (no client state), so
                    // it is safe on whatever thread Fabric dispatches this handler on. The result
                    // is never mutated after the volatile store, so readers see a stable snapshot.
                    DistillationConfig synced = DistillationConfig.fromJson(payload.configJson());
                    context.client().execute(() -> {
                        ClientDistillationConfig.setServerConfig(synced);
                        // A config change can add or drop §2/§5/§6 lines from the graph — rebuild viewers.
                        BrewingViewerRefresh.refreshViewers();
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(DiscoverySyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (payload.replace()) {
                        ClientDiscoveryState.setAll(payload.recipeIds());
                    } else {
                        ClientDiscoveryState.addAll(payload.recipeIds());
                    }
                    // A new discovery unhides its conversion in the (filtered) recipe viewers.
                    BrewingViewerRefresh.refreshViewers();
                }));
    }
}
