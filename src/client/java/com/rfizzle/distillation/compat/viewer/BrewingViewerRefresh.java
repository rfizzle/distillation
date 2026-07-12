package com.rfizzle.distillation.compat.viewer;

import com.rfizzle.distillation.compat.emi.EmiBrewingRefresh;
import com.rfizzle.distillation.compat.jei.JeiBrewingRefresh;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Rebuilds each installed recipe viewer's brewing list after the discovery set or synced config
 * changes at runtime — the viewer would otherwise keep showing the list it built at load. Call on the
 * client thread. REI is intentionally omitted: it exposes no safe programmatic reload, so it picks the
 * fresh snapshot up on rejoin / {@code /reload} / F3+T instead. Each viewer is guarded by
 * {@code isModLoaded} so its classes never load unless present.
 */
public final class BrewingViewerRefresh {

    private BrewingViewerRefresh() {
    }

    public static void refreshViewers() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("emi")) {
            EmiBrewingRefresh.reload();
        }
        if (loader.isModLoaded("jei")) {
            JeiBrewingRefresh.reload();
        }
    }
}
