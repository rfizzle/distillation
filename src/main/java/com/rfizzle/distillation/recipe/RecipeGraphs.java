package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.config.DistillationConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Serves the current {@link RecipeGraph} for a brewing registry. The graph is deterministic from
 * the registry and the {@code enableMissingBrews} toggle, so entries are cached per
 * {@link PotionBrewing} instance keyed by the toggle's value — a config flip (reload, ModMenu
 * commit) is picked up by the very next lookup, which is how the spec's "rebuilt at server start,
 * datapack reload, and config reload" freshness contract is met without bespoke wiring at each
 * trigger. {@code SERVER_STARTED} eagerly warms the server's entry and logs the conversion count.
 *
 * <p>Server and client hold distinct {@code PotionBrewing} instances (an integrated server hosts
 * both in one JVM), hence a map rather than a single slot. Keys are weak so an entry dies with its
 * server or connection; values never reference the key. Lookups are a lock + map read — the
 * per-tick {@code isBrewable} path never rebuilds unless the registry or toggle actually changed.
 */
public final class RecipeGraphs {

    private record Entry(boolean missingBrewsEnabled, RecipeGraph graph) {
    }

    private static final Map<PotionBrewing, Entry> CACHE = new WeakHashMap<>();

    // Set from client init: the synced-first config view (mc-config precedence). Null in a
    // dedicated-server JVM, where the local config is authoritative.
    @Nullable
    private static volatile Supplier<DistillationConfig> clientConfigSupplier;

    private RecipeGraphs() {
    }

    public static void registerLifecycleHandlers() {
        ServerLifecycleEvents.SERVER_STARTED.register(RecipeGraphs::warm);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> warm(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (CACHE) {
                CACHE.clear();
            }
        });
    }

    /** Wires the client's synced-first config view in; called from client init only. */
    public static void setClientConfigSupplier(Supplier<DistillationConfig> supplier) {
        clientConfigSupplier = supplier;
    }

    /** The graph for a level's brewing registry, under the side-appropriate config. */
    public static RecipeGraph forLevel(Level level) {
        boolean enabled = level.isClientSide
                ? effectiveConfig().enableMissingBrews
                : Distillation.getConfig().enableMissingBrews;
        return lookup(level.potionBrewing(), enabled);
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

    public static RecipeGraph lookup(PotionBrewing brewing, boolean missingBrewsEnabled) {
        synchronized (CACHE) {
            Entry entry = CACHE.get(brewing);
            if (entry == null || entry.missingBrewsEnabled() != missingBrewsEnabled) {
                Set<ResourceLocation> excluded =
                        missingBrewsEnabled ? Set.of() : DistillationBrews.ownedRecipeIds();
                entry = new Entry(missingBrewsEnabled, RecipeGraph.fromBrewing(brewing, excluded));
                CACHE.put(brewing, entry);
            }
            return entry.graph();
        }
    }

    private static void warm(MinecraftServer server) {
        RecipeGraph graph = lookup(server.potionBrewing(), Distillation.getConfig().enableMissingBrews);
        Distillation.LOGGER.info("Recipe graph built: {} conversions", graph.conversions().size());
    }
}
