package com.rfizzle.distillation.client;

import com.rfizzle.distillation.client.config.ClientDistillationConfig;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.client.net.ClientPayloadHandlers;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class DistillationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPayloadHandlers.register();
        // Client-side graph lookups (the menu's ingredient slot) honor the server's synced
        // gameplay config, falling back to the local file offline.
        RecipeGraphs.setClientConfigSupplier(ClientDistillationConfig::effective);
        // Clear synced server state on disconnect so stale rules and another server's
        // discoveries never bleed into the next world or singleplayer session.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientDistillationConfig.clear();
            ClientDiscoveryState.clear();
        });
    }
}
