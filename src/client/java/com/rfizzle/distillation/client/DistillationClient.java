package com.rfizzle.distillation.client;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.client.config.ClientDistillationConfig;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.client.net.ClientPayloadHandlers;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Items;

public class DistillationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPayloadHandlers.register();
        // The half-empty bottle render (SPEC §4): a model-override predicate on the vanilla potion,
        // 1 when the stack carries the draught marker. The vanilla potion tint (keyed on item
        // identity) still colors the liquid layer, so no ItemColor of our own is needed.
        ItemProperties.register(Items.POTION, Distillation.id("draught"),
                (stack, level, entity, seed) -> stack.has(DistillationItems.DRAUGHT) ? 1.0F : 0.0F);
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
