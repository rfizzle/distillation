package com.rfizzle.distillation.compat.emi;

import com.rfizzle.distillation.Distillation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Forces EMI to rebuild its recipe list after the discovery set or config changes at runtime — the
 * only way EMI's browse-all category picks up a fresh {@code recipeViewerShowsUndiscovered} filter on
 * a dedicated server. EMI's reload entry point lives outside the {@code :api} artifact, so it is
 * reached by reflection; this class holds no EMI imports and is safe to load when EMI is absent. It
 * latches off on the first failure, degrading to rejoin-refresh rather than throwing on every sync.
 */
public final class EmiBrewingRefresh {

    private static volatile boolean unavailable = false;

    private EmiBrewingRefresh() {
    }

    public static void reload() {
        if (unavailable) {
            return;
        }
        try {
            Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            MethodHandles.publicLookup()
                    .findStatic(reloadManager, "reloadRecipes", MethodType.methodType(void.class))
                    .invoke();
        } catch (Throwable t) {
            unavailable = true;
            Distillation.LOGGER.warn("EMI brewing-category reload unavailable; it refreshes on rejoin instead", t);
        }
    }
}
