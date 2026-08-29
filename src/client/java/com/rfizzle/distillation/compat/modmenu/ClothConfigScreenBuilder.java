package com.rfizzle.distillation.compat.modmenu;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.config.DistillationConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen: two categories mirroring the SPEC §Configuration tables (Server /
 * Client), every key with a {@code config.distillation.*} label and tooltip, sliders and fields
 * carrying the same ranges {@link DistillationConfig#clamp()} enforces, and a re-clamp before save
 * so the screen can never persist an out-of-range value. The screen edits a working copy of the
 * live config; saving clamps the copy, publishes it with a single volatile reference swap, and
 * writes it to disk — the live object is never mutated in place, so a concurrent reader (e.g. the
 * integrated-server thread) only ever sees whole snapshots. Only classloaded when Cloth Config is
 * present (see {@link ModMenuIntegration}).
 */
final class ClothConfigScreenBuilder {

    private ClothConfigScreenBuilder() {
    }

    static Screen build(Screen parent) {
        DistillationConfig config = Distillation.getConfig().copy();
        DistillationConfig defaults = new DistillationConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.distillation.title"))
                .setSavingRunnable(() -> {
                    config.clamp();
                    Distillation.updateConfig(config);
                    config.save();
                });

        ConfigEntryBuilder entry = builder.entryBuilder();

        ConfigCategory server = builder.getOrCreateCategory(
                Component.translatable("config.distillation.category.server"));

        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableDiscovery"),
                        config.enableDiscovery)
                .setTooltip(Component.translatable("config.distillation.enableDiscovery.tooltip"))
                .setDefaultValue(defaults.enableDiscovery)
                .setSaveConsumer(v -> config.enableDiscovery = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableMurkyDraughts"),
                        config.enableMurkyDraughts)
                .setTooltip(Component.translatable("config.distillation.enableMurkyDraughts.tooltip"))
                .setDefaultValue(defaults.enableMurkyDraughts)
                .setSaveConsumer(v -> config.enableMurkyDraughts = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.startDiscovered"),
                        config.startDiscovered)
                .setTooltip(Component.translatable("config.distillation.startDiscovered.tooltip"))
                .setDefaultValue(defaults.startDiscovered)
                .setSaveConsumer(v -> config.startDiscovered = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableRecipeNotes"),
                        config.enableRecipeNotes)
                .setTooltip(Component.translatable("config.distillation.enableRecipeNotes.tooltip"))
                .setDefaultValue(defaults.enableRecipeNotes)
                .setSaveConsumer(v -> config.enableRecipeNotes = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableMissingBrews"),
                        config.enableMissingBrews)
                .setTooltip(Component.translatable("config.distillation.enableMissingBrews.tooltip"))
                .setDefaultValue(defaults.enableMissingBrews)
                .setSaveConsumer(v -> config.enableMissingBrews = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableBatchBrewing"),
                        config.enableBatchBrewing)
                .setTooltip(Component.translatable("config.distillation.enableBatchBrewing.tooltip"))
                .setDefaultValue(defaults.enableBatchBrewing)
                .setSaveConsumer(v -> config.enableBatchBrewing = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.batchIngredientCost"),
                        config.batchIngredientCost, 2, 6)
                .setTooltip(Component.translatable("config.distillation.batchIngredientCost.tooltip"))
                .setDefaultValue(defaults.batchIngredientCost)
                .setSaveConsumer(v -> config.batchIngredientCost = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.batchFuelCost"),
                        config.batchFuelCost, 1, 4)
                .setTooltip(Component.translatable("config.distillation.batchFuelCost.tooltip"))
                .setDefaultValue(defaults.batchFuelCost)
                .setSaveConsumer(v -> config.batchFuelCost = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableComparatorOutput"),
                        config.enableComparatorOutput)
                .setTooltip(Component.translatable("config.distillation.enableComparatorOutput.tooltip"))
                .setDefaultValue(defaults.enableComparatorOutput)
                .setSaveConsumer(v -> config.enableComparatorOutput = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableTippedArrows"),
                        config.enableTippedArrows)
                .setTooltip(Component.translatable("config.distillation.enableTippedArrows.tooltip"))
                .setDefaultValue(defaults.enableTippedArrows)
                .setSaveConsumer(v -> config.enableTippedArrows = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.tippedArrowsPerDip"),
                        config.tippedArrowsPerDip, 1, 16)
                .setTooltip(Component.translatable("config.distillation.tippedArrowsPerDip.tooltip"))
                .setDefaultValue(defaults.tippedArrowsPerDip)
                .setSaveConsumer(v -> config.tippedArrowsPerDip = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableHonestDurations"),
                        config.enableHonestDurations)
                .setTooltip(Component.translatable("config.distillation.enableHonestDurations.tooltip"))
                .setDefaultValue(defaults.enableHonestDurations)
                .setSaveConsumer(v -> config.enableHonestDurations = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableDraughts"),
                        config.enableDraughts)
                .setTooltip(Component.translatable("config.distillation.enableDraughts.tooltip"))
                .setDefaultValue(defaults.enableDraughts)
                .setSaveConsumer(v -> config.enableDraughts = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableTopUpDrinking"),
                        config.enableTopUpDrinking)
                .setTooltip(Component.translatable("config.distillation.enableTopUpDrinking.tooltip"))
                .setDefaultValue(defaults.enableTopUpDrinking)
                .setSaveConsumer(v -> config.enableTopUpDrinking = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableFlask"),
                        config.enableFlask)
                .setTooltip(Component.translatable("config.distillation.enableFlask.tooltip"))
                .setDefaultValue(defaults.enableFlask)
                .setSaveConsumer(v -> config.enableFlask = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enablePremiumBrews"),
                        config.enablePremiumBrews)
                .setTooltip(Component.translatable("config.distillation.enablePremiumBrews.tooltip"))
                .setDefaultValue(defaults.enablePremiumBrews)
                .setSaveConsumer(v -> config.enablePremiumBrews = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableAntidotes"),
                        config.enableAntidotes)
                .setTooltip(Component.translatable("config.distillation.enableAntidotes.tooltip"))
                .setDefaultValue(defaults.enableAntidotes)
                .setSaveConsumer(v -> config.enableAntidotes = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableThrownRebalance"),
                        config.enableThrownRebalance)
                .setTooltip(Component.translatable("config.distillation.enableThrownRebalance.tooltip"))
                .setDefaultValue(defaults.enableThrownRebalance)
                .setSaveConsumer(v -> config.enableThrownRebalance = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enableAttunedSplash"),
                        config.enableAttunedSplash)
                .setTooltip(Component.translatable("config.distillation.enableAttunedSplash.tooltip"))
                .setDefaultValue(defaults.enableAttunedSplash)
                .setSaveConsumer(v -> config.enableAttunedSplash = v)
                .build());
        server.addEntry(entry.startFloatField(
                        Component.translatable("config.distillation.splashDurationFactor"),
                        config.splashDurationFactor)
                .setTooltip(Component.translatable("config.distillation.splashDurationFactor.tooltip"))
                .setMin(0.5f).setMax(1.0f)
                .setDefaultValue(defaults.splashDurationFactor)
                .setSaveConsumer(v -> config.splashDurationFactor = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.lingeringCloudDurationTicks"),
                        config.lingeringCloudDurationTicks, 600, 2400)
                .setTooltip(Component.translatable("config.distillation.lingeringCloudDurationTicks.tooltip"))
                .setDefaultValue(defaults.lingeringCloudDurationTicks)
                .setSaveConsumer(v -> config.lingeringCloudDurationTicks = v)
                .build());
        server.addEntry(entry.startFloatField(
                        Component.translatable("config.distillation.lingeringCloudRadius"),
                        config.lingeringCloudRadius)
                .setTooltip(Component.translatable("config.distillation.lingeringCloudRadius.tooltip"))
                .setMin(3.0f).setMax(6.0f)
                .setDefaultValue(defaults.lingeringCloudRadius)
                .setSaveConsumer(v -> config.lingeringCloudRadius = v)
                .build());

        ConfigCategory client = builder.getOrCreateCategory(
                Component.translatable("config.distillation.category.client"));

        client.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.showVaporHints"),
                        config.client.showVaporHints)
                .setTooltip(Component.translatable("config.distillation.showVaporHints.tooltip"))
                .setDefaultValue(defaults.client.showVaporHints)
                .setSaveConsumer(v -> config.client.showVaporHints = v)
                .build());
        client.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.recipeViewerShowsUndiscovered"),
                        config.client.recipeViewerShowsUndiscovered)
                .setTooltip(Component.translatable("config.distillation.recipeViewerShowsUndiscovered.tooltip"))
                .setDefaultValue(defaults.client.recipeViewerShowsUndiscovered)
                .setSaveConsumer(v -> config.client.recipeViewerShowsUndiscovered = v)
                .build());
        client.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.smoothNightVisionFade"),
                        config.client.smoothNightVisionFade)
                .setTooltip(Component.translatable("config.distillation.smoothNightVisionFade.tooltip"))
                .setDefaultValue(defaults.client.smoothNightVisionFade)
                .setSaveConsumer(v -> config.client.smoothNightVisionFade = v)
                .build());

        return builder.build();
    }
}
