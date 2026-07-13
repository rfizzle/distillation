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
                        Component.translatable("config.distillation.enable_discovery"),
                        config.enableDiscovery)
                .setTooltip(Component.translatable("config.distillation.enable_discovery.tooltip"))
                .setDefaultValue(defaults.enableDiscovery)
                .setSaveConsumer(v -> config.enableDiscovery = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_murky_draughts"),
                        config.enableMurkyDraughts)
                .setTooltip(Component.translatable("config.distillation.enable_murky_draughts.tooltip"))
                .setDefaultValue(defaults.enableMurkyDraughts)
                .setSaveConsumer(v -> config.enableMurkyDraughts = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.start_discovered"),
                        config.startDiscovered)
                .setTooltip(Component.translatable("config.distillation.start_discovered.tooltip"))
                .setDefaultValue(defaults.startDiscovered)
                .setSaveConsumer(v -> config.startDiscovered = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_missing_brews"),
                        config.enableMissingBrews)
                .setTooltip(Component.translatable("config.distillation.enable_missing_brews.tooltip"))
                .setDefaultValue(defaults.enableMissingBrews)
                .setSaveConsumer(v -> config.enableMissingBrews = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_batch_brewing"),
                        config.enableBatchBrewing)
                .setTooltip(Component.translatable("config.distillation.enable_batch_brewing.tooltip"))
                .setDefaultValue(defaults.enableBatchBrewing)
                .setSaveConsumer(v -> config.enableBatchBrewing = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.batch_ingredient_cost"),
                        config.batchIngredientCost, 2, 6)
                .setTooltip(Component.translatable("config.distillation.batch_ingredient_cost.tooltip"))
                .setDefaultValue(defaults.batchIngredientCost)
                .setSaveConsumer(v -> config.batchIngredientCost = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.batch_fuel_cost"),
                        config.batchFuelCost, 1, 4)
                .setTooltip(Component.translatable("config.distillation.batch_fuel_cost.tooltip"))
                .setDefaultValue(defaults.batchFuelCost)
                .setSaveConsumer(v -> config.batchFuelCost = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_tipped_arrows"),
                        config.enableTippedArrows)
                .setTooltip(Component.translatable("config.distillation.enable_tipped_arrows.tooltip"))
                .setDefaultValue(defaults.enableTippedArrows)
                .setSaveConsumer(v -> config.enableTippedArrows = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.tipped_arrows_per_dip"),
                        config.tippedArrowsPerDip, 1, 16)
                .setTooltip(Component.translatable("config.distillation.tipped_arrows_per_dip.tooltip"))
                .setDefaultValue(defaults.tippedArrowsPerDip)
                .setSaveConsumer(v -> config.tippedArrowsPerDip = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_honest_durations"),
                        config.enableHonestDurations)
                .setTooltip(Component.translatable("config.distillation.enable_honest_durations.tooltip"))
                .setDefaultValue(defaults.enableHonestDurations)
                .setSaveConsumer(v -> config.enableHonestDurations = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_draughts"),
                        config.enableDraughts)
                .setTooltip(Component.translatable("config.distillation.enable_draughts.tooltip"))
                .setDefaultValue(defaults.enableDraughts)
                .setSaveConsumer(v -> config.enableDraughts = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_premium_brews"),
                        config.enablePremiumBrews)
                .setTooltip(Component.translatable("config.distillation.enable_premium_brews.tooltip"))
                .setDefaultValue(defaults.enablePremiumBrews)
                .setSaveConsumer(v -> config.enablePremiumBrews = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_antidotes"),
                        config.enableAntidotes)
                .setTooltip(Component.translatable("config.distillation.enable_antidotes.tooltip"))
                .setDefaultValue(defaults.enableAntidotes)
                .setSaveConsumer(v -> config.enableAntidotes = v)
                .build());
        server.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.enable_thrown_rebalance"),
                        config.enableThrownRebalance)
                .setTooltip(Component.translatable("config.distillation.enable_thrown_rebalance.tooltip"))
                .setDefaultValue(defaults.enableThrownRebalance)
                .setSaveConsumer(v -> config.enableThrownRebalance = v)
                .build());
        server.addEntry(entry.startFloatField(
                        Component.translatable("config.distillation.splash_duration_factor"),
                        config.splashDurationFactor)
                .setTooltip(Component.translatable("config.distillation.splash_duration_factor.tooltip"))
                .setMin(0.5f).setMax(1.0f)
                .setDefaultValue(defaults.splashDurationFactor)
                .setSaveConsumer(v -> config.splashDurationFactor = v)
                .build());
        server.addEntry(entry.startIntSlider(
                        Component.translatable("config.distillation.lingering_cloud_duration_ticks"),
                        config.lingeringCloudDurationTicks, 600, 2400)
                .setTooltip(Component.translatable("config.distillation.lingering_cloud_duration_ticks.tooltip"))
                .setDefaultValue(defaults.lingeringCloudDurationTicks)
                .setSaveConsumer(v -> config.lingeringCloudDurationTicks = v)
                .build());
        server.addEntry(entry.startFloatField(
                        Component.translatable("config.distillation.lingering_cloud_radius"),
                        config.lingeringCloudRadius)
                .setTooltip(Component.translatable("config.distillation.lingering_cloud_radius.tooltip"))
                .setMin(3.0f).setMax(6.0f)
                .setDefaultValue(defaults.lingeringCloudRadius)
                .setSaveConsumer(v -> config.lingeringCloudRadius = v)
                .build());

        ConfigCategory client = builder.getOrCreateCategory(
                Component.translatable("config.distillation.category.client"));

        client.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.show_vapor_hints"),
                        config.client.showVaporHints)
                .setTooltip(Component.translatable("config.distillation.show_vapor_hints.tooltip"))
                .setDefaultValue(defaults.client.showVaporHints)
                .setSaveConsumer(v -> config.client.showVaporHints = v)
                .build());
        client.addEntry(entry.startBooleanToggle(
                        Component.translatable("config.distillation.recipe_viewer_shows_undiscovered"),
                        config.client.recipeViewerShowsUndiscovered)
                .setTooltip(Component.translatable("config.distillation.recipe_viewer_shows_undiscovered.tooltip"))
                .setDefaultValue(defaults.client.recipeViewerShowsUndiscovered)
                .setSaveConsumer(v -> config.client.recipeViewerShowsUndiscovered = v)
                .build());

        return builder.build();
    }
}
