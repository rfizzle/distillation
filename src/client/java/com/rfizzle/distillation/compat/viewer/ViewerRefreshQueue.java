package com.rfizzle.distillation.compat.viewer;

/**
 * The pending-refresh flag behind {@link BrewingViewerRefresh}'s one-tick debounce. A sync that
 * invalidates the viewers calls {@link #request()}; the client tick drains it with {@link #drain()},
 * which reports whether a rebuild is owed and clears the flag in the same step. Several requests
 * landing between two drains therefore cost one rebuild, and a request arriving after a drain starts
 * a fresh cycle rather than being swallowed.
 *
 * <p>Deliberately free of Minecraft and Fabric types so the cadence is unit-testable without a game.
 * The flag is written and read only from the client thread (both sync handlers hop there before
 * touching it), so the {@code volatile} is for visibility alone — no compound operation needs a lock.
 */
final class ViewerRefreshQueue {

    private static volatile boolean pending;

    private ViewerRefreshQueue() {
    }

    /** Marks the viewers as owing a rebuild. Idempotent within a tick window. */
    static void request() {
        pending = true;
    }

    /**
     * Takes the pending request, if any.
     *
     * @return {@code true} when a rebuild was owed — the caller must perform exactly one
     */
    static boolean drain() {
        if (!pending) {
            return false;
        }
        pending = false;
        return true;
    }

    /** Drops any pending request, so one server's sync never triggers a rebuild in the next world. */
    static void clear() {
        pending = false;
    }
}
