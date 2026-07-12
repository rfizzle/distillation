package com.rfizzle.distillation.compat.jei;

/**
 * The seam the viewer-refresh dispatcher pushes a JEI rebuild through. The JEI plugin binds a
 * {@link Runnable} (its idempotent hide-then-add refresh) once it has captured the runtime; this
 * class holds no JEI imports, so it is safe to reference from the always-loaded dispatcher and to
 * load when JEI is absent.
 */
public final class JeiBrewingRefresh {

    private static volatile Runnable refresher;

    private JeiBrewingRefresh() {
    }

    static void bind(Runnable runnable) {
        refresher = runnable;
    }

    static void unbind() {
        refresher = null;
    }

    public static void reload() {
        Runnable local = refresher;
        if (local != null) {
            local.run();
        }
    }
}
