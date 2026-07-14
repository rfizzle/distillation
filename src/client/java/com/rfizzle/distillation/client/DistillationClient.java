package com.rfizzle.distillation.client;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.client.config.ClientDistillationConfig;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.client.net.ClientPayloadHandlers;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.FlaskItem;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class DistillationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPayloadHandlers.register();
        // The half-empty bottle render (SPEC §4): a model-override predicate on the vanilla potion,
        // 1 when the stack carries the draught marker. The vanilla potion tint (keyed on item
        // identity) still colors the liquid layer, so no ItemColor of our own is needed.
        ItemProperties.register(Items.POTION, Distillation.id("draught"),
                (stack, level, entity, seed) -> stack.has(DistillationItems.DRAUGHT) ? 1.0F : 0.0F);
        // The antidote bottle render (SPEC §6): the shared antidote model for any antidote potion,
        // tinted per cure by the vanilla potion color provider reading the deepened liquid color.
        ItemProperties.register(Items.POTION, Distillation.id("antidote"),
                (stack, level, entity, seed) -> isAntidote(stack) ? 1.0F : 0.0F);
        // The flask render (SPEC §12): a "filled" predicate routes a flask holding a dose to the
        // filled model (copper-and-glass base plus a liquid layer), and an item color tints only that
        // liquid layer (tintIndex 1) to the stored brew's color — the vessel (tintIndex 0) stays bare.
        ItemProperties.register(DistillationItems.FLASK, Distillation.id("filled"),
                (stack, level, entity, seed) -> FlaskItem.doses(stack) > 0 ? 1.0F : 0.0F);
        ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex == 1
                        ? stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor()
                        : -1,
                DistillationItems.FLASK);
        // Client-side graph lookups (the menu's ingredient slot) honor the server's synced
        // gameplay config, falling back to the local file offline.
        RecipeGraphs.setClientConfigSupplier(ClientDistillationConfig::effective);
        // The client world's graph, so a recipe-note tooltip (no level in reach) resolves the
        // recipe it points at; null at the title screen, before any world loads.
        RecipeGraphs.setClientGraphSupplier(() -> {
            Minecraft client = Minecraft.getInstance();
            return client.level == null ? null : RecipeGraphs.forLevel(client.level);
        });
        // Clear synced server state on disconnect so stale rules and another server's
        // discoveries never bleed into the next world or singleplayer session.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientDistillationConfig.clear();
            ClientDiscoveryState.clear();
        });
    }

    private static boolean isAntidote(ItemStack stack) {
        ResourceLocation id = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location)
                .orElse(null);
        return id != null && Antidotes.isAntidote(id);
    }
}
