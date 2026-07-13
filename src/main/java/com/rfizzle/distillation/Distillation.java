package com.rfizzle.distillation;

import com.rfizzle.distillation.advancement.DistillationAdvancements;
import com.rfizzle.distillation.advancement.DistillationCriteria;
import com.rfizzle.distillation.arrow.CauldronDipInteractions;
import com.rfizzle.distillation.arrow.ChargedCauldronParticles;
import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.brew.DistillationBrews;
import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.command.DistillationCommand;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.DistillationAttachments;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.RecipeNoteServerHandler;
import com.rfizzle.distillation.network.DistillationNetworking;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import com.rfizzle.distillation.sound.DistillationSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Distillation implements ModInitializer {
    public static final String MOD_ID = "distillation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // The active config, published only by whole-reference swap (lazy first load, reload, or the
    // ModMenu screen's commit). Volatile so every reader sees the swap atomically.
    private static volatile DistillationConfig config;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        getConfig(); // eager first load, so later callers never pay the lazy path in play
        DistillationPotions.register();
        PremiumBrews.register(); // reads the §2 holders above; premium conversions need it below
        Antidotes.registerEffect(); // the cleanse effect must exist before its antidote potions
        Antidotes.register();
        DistillationBrews.registerConversions();
        PremiumBrews.registerConversions();
        Antidotes.registerConversions();
        DistillationItems.register();
        DistillationSounds.register();
        DistillationAttachments.init();
        CauldronDipInteractions.register();
        ChargedCauldronParticles.register();
        RecipeGraphs.registerLifecycleHandlers();
        DistillationNetworking.registerPayloads();
        DistillationNetworking.registerLifecycleHandlers();
        RecipeNoteServerHandler.register();
        DistillationCommand.register();
        DistillationCriteria.register(); // before any advancement JSON is deserialized (server start)
        DistillationAdvancements.register();
        LOGGER.info("Distillation initialized");
    }

    public static DistillationConfig getConfig() {
        DistillationConfig local = config;
        if (local == null) {
            synchronized (Distillation.class) {
                local = config;
                if (local == null) {
                    config = local = DistillationConfig.load();
                }
            }
        }
        return local;
    }

    /**
     * Publishes a fully-built replacement config with a single volatile reference swap — the
     * ModMenu screen's commit point for its clamped working copy. Readers switch snapshots
     * atomically; the live object is never mutated in place.
     */
    public static void updateConfig(DistillationConfig updated) {
        config = updated;
    }

    /** Re-reads {@code config/distillation.json}; readers see the reference swap atomically. */
    public static void reloadConfig() {
        config = DistillationConfig.load();
    }

    /**
     * Re-reads the config and re-broadcasts the gameplay surface to every connected client, so a
     * live change reaches them without a reconnect. The future {@code /distillation reload}
     * command wires here.
     */
    public static void reloadConfig(MinecraftServer server) {
        reloadConfig();
        DistillationNetworking.syncConfigToAll(server);
    }
}
