package com.rfizzle.distillation.compat.viewer;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.emi.EmiBrewingRefresh;
import com.rfizzle.distillation.compat.jei.JeiBrewingRefresh;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Rebuilds each installed recipe viewer's brewing list after the discovery set or synced config
 * changes at runtime — the viewer would otherwise keep showing the list it built at load. Callers
 * ask for a rebuild with {@link #requestRefresh()} on the client thread; the request is coalesced
 * and flushed at the end of the tick, so the config and discovery syncs that arrive back-to-back on
 * join rebuild each viewer once rather than twice. REI is intentionally omitted: it exposes no safe
 * programmatic reload, so it picks the fresh snapshot up on rejoin / {@code /reload} / F3+T instead.
 * Each viewer is guarded by {@code isModLoaded} so its classes never load unless present.
 */
public final class BrewingViewerRefresh {

    private BrewingViewerRefresh() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> flush());
        // The pending flag mirrors server-pushed state, so it clears with the rest of it — a sync
        // received just before a disconnect must not rebuild against the next world's snapshot.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ViewerRefreshQueue.clear());
    }

    /** Marks the viewers stale; the rebuild runs once at the end of the current client tick. */
    public static void requestRefresh() {
        ViewerRefreshQueue.request();
    }

    private static void flush() {
        if (!ViewerRefreshQueue.drain()) {
            return;
        }
        try {
            refreshViewers();
        } catch (Throwable t) {
            // A viewer failing its rebuild must not take the client tick event down with it; the
            // per-viewer shims latch their own failures, so this is the backstop for anything else.
            // Throwable rather than Exception because both shims reach their viewer through an API
            // pinned at compile time — a viewer that drifted from it throws NoSuchMethodError.
            Distillation.LOGGER.error("Failed to refresh the brewing recipe viewers", t);
        }
    }

    private static void refreshViewers() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("emi")) {
            EmiBrewingRefresh.reload();
        }
        if (loader.isModLoaded("jei")) {
            JeiBrewingRefresh.reload();
        }
    }
}
