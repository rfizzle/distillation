package com.rfizzle.distillation.client.config;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.config.DistillationConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder for the server-authoritative gameplay config pushed by
 * {@link com.rfizzle.distillation.network.ConfigSyncPayload}.
 *
 * <p>Gameplay-affecting client code must read {@link #effective()} rather than
 * {@link Distillation#getConfig()} directly, so it honors the server's values with the local
 * config as a fallback only when no server value is present (true singleplayer, or before the join
 * payload arrives). The client-only {@code client} preferences are excluded from the synced view
 * and continue to be read from the local config directly.
 *
 * <p>The synced copy is cleared on disconnect so stale server rules never bleed into the next
 * world. The field is {@code volatile}: the receiver writes it on the client thread while
 * renderers read it.
 */
public final class ClientDistillationConfig {

    @Nullable
    private static volatile DistillationConfig serverConfig;

    private ClientDistillationConfig() {
    }

    /** Stores the config decoded from a {@link com.rfizzle.distillation.network.ConfigSyncPayload}. */
    public static void setServerConfig(@Nullable DistillationConfig config) {
        serverConfig = config;
    }

    /** The raw server-synced config, or {@code null} when none has arrived (standalone/singleplayer). */
    @Nullable
    public static DistillationConfig getServerConfig() {
        return serverConfig;
    }

    /** Clears the synced copy; call on disconnect so the next world falls back to the local config. */
    public static void clear() {
        serverConfig = null;
    }

    /**
     * The config a gameplay-affecting client reader should use: the server-synced copy when
     * present, otherwise the local file. Never {@code null} in game — the local config is always
     * loaded by the time a client screen or tooltip renders.
     */
    public static DistillationConfig effective() {
        DistillationConfig synced = serverConfig;
        return synced != null ? synced : Distillation.getConfig();
    }
}
