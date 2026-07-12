package com.rfizzle.distillation.batch;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Threads the identity of the player doing a brewing-stand menu click down to the block entity's
 * {@code setItem} ({@code design/SPEC.md} §3 Ownership). A player insert into the ingredient slot
 * runs inside {@code AbstractContainerMenu.clicked} with the player set here, so {@code setItem}
 * records them as the batch owner; a hopper insert runs with no context and disowns the stand.
 *
 * <p>Set on both logical sides but read only by the server-side block entity; the {@link ThreadLocal}
 * keeps an integrated server's client and server threads independent.
 */
public final class BatchOwnerContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private BatchOwnerContext() {
    }

    /** Marks the player whose menu click is now running (null clears it). */
    public static void set(@Nullable UUID player) {
        if (player == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(player);
        }
    }

    /** The player whose menu click is running on this thread, or {@code null} for a hopper/automation. */
    @Nullable
    public static UUID current() {
        return CURRENT.get();
    }
}
