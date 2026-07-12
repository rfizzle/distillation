package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.config.DistillationConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Serves the current {@link RecipeGraph} for a brewing registry. The graph is deterministic from
 * the registry and the {@code enableMissingBrews} / {@code enablePremiumBrews} toggles, so entries
 * are cached per {@link PotionBrewing} instance keyed by both toggles' values — a config flip
 * (reload, ModMenu commit) is picked up by the very next lookup, which is how the spec's "rebuilt at
 * server start, datapack reload, and config reload" freshness contract is met without bespoke wiring
 * at each trigger. {@code SERVER_STARTED} eagerly warms the server's entry and logs the conversion
 * count.
 *
 * <p>Server and client hold distinct {@code PotionBrewing} instances (an integrated server hosts
 * both in one JVM), hence a map rather than a single slot. Keys are weak so an entry dies with its
 * server or connection; values never reference the key. Lookups are a lock + map read — the
 * per-tick {@code isBrewable} path never rebuilds unless the registry or toggle actually changed.
 */
public final class RecipeGraphs {

    private record Entry(boolean missingBrewsEnabled, boolean premiumBrewsEnabled, boolean antidotesEnabled,
                         RecipeGraph graph) {
    }

    private static final Map<PotionBrewing, Entry> CACHE = new WeakHashMap<>();

    // Set from client init: the synced-first config view (mc-config precedence). Null in a
    // dedicated-server JVM, where the local config is authoritative.
    @Nullable
    private static volatile Supplier<DistillationConfig> clientConfigSupplier;

    // The running server, so the server-authoritative Public API can resolve the current graph
    // without a level in hand ({@code DistillationAPI.getRecipeIds}). Null when no server runs.
    @Nullable
    private static volatile MinecraftServer currentServer;

    private RecipeGraphs() {
    }

    public static void registerLifecycleHandlers() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            warm(server);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> warm(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            synchronized (CACHE) {
                CACHE.clear();
            }
        });
    }

    /**
     * The current recipe graph's ids, server-authoritative — the {@code DistillationAPI.getRecipeIds}
     * source. An empty set when no server is running (a client with no world, or before start).
     */
    public static Set<ResourceLocation> currentRecipeIds() {
        MinecraftServer server = currentServer;
        if (server == null) {
            return Set.of();
        }
        DistillationConfig config = Distillation.getConfig();
        return lookup(server.potionBrewing(), config.enableMissingBrews, config.enablePremiumBrews,
                config.enableAntidotes).ids();
    }

    /**
     * Drops every cached graph so the very next lookup rebuilds from the live brewing registry —
     * {@code /distillation reload}'s explicit rebuild. The cache key only tracks the feature
     * toggles, so without this a reload that changes nothing else would silently serve the stale
     * graph.
     */
    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** Wires the client's synced-first config view in; called from client init only. */
    public static void setClientConfigSupplier(Supplier<DistillationConfig> supplier) {
        clientConfigSupplier = supplier;
    }

    /** The graph for a level's brewing registry, under the side-appropriate config. */
    public static RecipeGraph forLevel(Level level) {
        DistillationConfig config = level.isClientSide ? effectiveConfig() : Distillation.getConfig();
        return lookup(level.potionBrewing(), config.enableMissingBrews, config.enablePremiumBrews,
                config.enableAntidotes);
    }

    /**
     * The side-appropriate config for callers with no level in reach (the menu's ingredient slot):
     * the client's synced-first view when this JVM has a client, the local config otherwise. On an
     * integrated server both views agree, so either logical side reads correct values.
     */
    public static DistillationConfig effectiveConfig() {
        Supplier<DistillationConfig> supplier = clientConfigSupplier;
        return supplier != null ? supplier.get() : Distillation.getConfig();
    }

    public static RecipeGraph lookup(PotionBrewing brewing, boolean missingBrewsEnabled, boolean premiumBrewsEnabled,
                                     boolean antidotesEnabled) {
        synchronized (CACHE) {
            Entry entry = CACHE.get(brewing);
            if (entry == null || entry.missingBrewsEnabled() != missingBrewsEnabled
                    || entry.premiumBrewsEnabled() != premiumBrewsEnabled
                    || entry.antidotesEnabled() != antidotesEnabled) {
                Set<ResourceLocation> excluded = new LinkedHashSet<>();
                if (!missingBrewsEnabled) {
                    excluded.addAll(DistillationBrews.ownedRecipeIds());
                    // A §2 line's premium concentration goes with the line it builds on.
                    excluded.addAll(PremiumBrews.distillationBackedRecipeIds());
                }
                if (!premiumBrewsEnabled) {
                    excluded.addAll(PremiumBrews.ownedRecipeIds());
                }
                if (!antidotesEnabled) {
                    excluded.addAll(Antidotes.ownedRecipeIds());
                }
                entry = new Entry(missingBrewsEnabled, premiumBrewsEnabled, antidotesEnabled,
                        RecipeGraph.fromBrewing(brewing, excluded));
                CACHE.put(brewing, entry);
            }
            return entry.graph();
        }
    }

    private static void warm(MinecraftServer server) {
        DistillationConfig config = Distillation.getConfig();
        RecipeGraph graph = lookup(server.potionBrewing(), config.enableMissingBrews, config.enablePremiumBrews,
                config.enableAntidotes);
        Distillation.LOGGER.info("Recipe graph built: {} conversions", graph.conversions().size());
    }
}
