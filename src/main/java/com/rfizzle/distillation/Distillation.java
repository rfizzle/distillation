package com.rfizzle.distillation;

import com.rfizzle.distillation.config.DistillationConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Distillation implements ModInitializer {
    public static final String MOD_ID = "distillation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // The active config. Volatile so a reload's reference swap is visible to every reader;
    // loaded once in onInitialize, before anything that reads it registers.
    private static volatile DistillationConfig config;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        config = DistillationConfig.load();
        LOGGER.info("Distillation initialized");
    }

    public static DistillationConfig getConfig() {
        return config;
    }

    /** Re-reads {@code config/distillation.json}; readers see the reference swap atomically. */
    public static void reloadConfig() {
        config = DistillationConfig.load();
    }
}
