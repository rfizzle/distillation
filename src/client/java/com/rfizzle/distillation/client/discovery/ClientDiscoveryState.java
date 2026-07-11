package com.rfizzle.distillation.client.discovery;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client-side holder for the player's discovery set, pushed by
 * {@link com.rfizzle.distillation.network.DiscoverySyncPayload} on join and on change. Read by the
 * client discovery surfaces (vapor hint tooltips, the recipes page, the recipe-viewer filter) as
 * they land; like the server's stored set, it may hold ids stale against the current graph —
 * readers intersect with the graph at read time.
 *
 * <p>Published only by whole-reference swap of an immutable snapshot (the receiver writes on the
 * client thread while renderers read), preserving discovery order. Cleared on disconnect so one
 * server's discoveries never bleed into the next world.
 */
public final class ClientDiscoveryState {

    private static volatile Set<ResourceLocation> discovered = Set.of();

    private ClientDiscoveryState() {
    }

    /** Replaces the set wholesale — a {@code replace} sync (join, forget, discover-all). */
    public static void setAll(Collection<ResourceLocation> recipeIds) {
        // LinkedHashSet, not Set.copyOf: iteration order is discovery order and is semantic.
        discovered = Collections.unmodifiableSet(new LinkedHashSet<>(recipeIds));
    }

    /** Appends newly discovered ids — the common single-discovery delta. */
    public static void addAll(Collection<ResourceLocation> recipeIds) {
        LinkedHashSet<ResourceLocation> next = new LinkedHashSet<>(discovered);
        next.addAll(recipeIds);
        discovered = Collections.unmodifiableSet(next);
    }

    /** The current snapshot, in discovery order; never {@code null}. */
    public static Set<ResourceLocation> discovered() {
        return discovered;
    }

    /** Clears the set; call on disconnect. */
    public static void clear() {
        discovered = Set.of();
    }
}
