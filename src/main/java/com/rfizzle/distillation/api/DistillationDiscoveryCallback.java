package com.rfizzle.distillation.api;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired server-side the first time a player discovers a recipe through play ({@code design/SPEC.md}
 * §1) — that is, when they take a freshly brewed output the stand has not taught them before, and
 * the discovery is newly recorded. Re-taking an already-known output does not fire it, and the
 * admin/bulk grants ({@code /distillation discover}, the {@code startDiscovered} join grant) record
 * silently without firing per the same rule that suppresses their toast.
 *
 * <p>A listener that throws is caught, logged, and skipped — a misbehaving observer can never break
 * discovery recording.
 */
@Stable
public interface DistillationDiscoveryCallback {

    Event<DistillationDiscoveryCallback> EVENT = EventFactory.createArrayBacked(DistillationDiscoveryCallback.class,
            listeners -> (player, recipeId) -> {
                for (DistillationDiscoveryCallback listener : listeners) {
                    try {
                        listener.onDiscover(player, recipeId);
                    } catch (Throwable t) {
                        Distillation.LOGGER.warn("A DistillationDiscoveryCallback listener threw; skipping", t);
                    }
                }
            });

    /**
     * @param player   the player who just discovered the recipe
     * @param recipeId the stable recipe id newly added to their discovery set
     */
    void onDiscover(ServerPlayer player, ResourceLocation recipeId);
}
