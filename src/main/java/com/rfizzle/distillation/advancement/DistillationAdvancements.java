package com.rfizzle.distillation.advancement;

import com.rfizzle.distillation.api.DistillationBrewCallback;
import com.rfizzle.distillation.api.DistillationDiscoveryCallback;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.discovery.FakePlayers;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the §9 advancement triggers to the systems they observe, keeping all advancement-firing out
 * of the gameplay classes themselves. It rides Distillation's own public callbacks (dogfooding the
 * discovery and brew events) for the derived milestones, and is called from the extraction hook for
 * the "obtain the bottle" milestones — the player-attributed, automation-safe moment, since the brew
 * choke point carries no player and can be hopper-driven.
 */
public final class DistillationAdvancements {

    private static boolean registered = false;

    private DistillationAdvancements() {
    }

    public static void register() {
        if (registered) {
            return; // idempotent — a second call must not double-register the event listeners
        }
        registered = true;
        DistillationDiscoveryCallback.EVENT.register((player, recipeId) -> onDiscover(player));
        DistillationBrewCallback.EVENT.register(DistillationAdvancements::onBrew);
    }

    /**
     * The extraction hook ({@code BrewingStandMenu$PotionSlot#onTake}, beside discovery recording):
     * taking a bottle from the stand is the player-attributed, hopper-safe moment for the "brew and
     * obtain" milestones. Fake players and automation grant nothing.
     */
    public static void onBottleTaken(Player player, ItemStack taken) {
        if (!(player instanceof ServerPlayer serverPlayer) || FakePlayers.isFakePlayer(serverPlayer)) {
            return;
        }
        if (taken.is(DistillationItems.MURKY_DRAUGHT)) {
            DistillationCriteria.MURKY_BOTTLED.trigger(serverPlayer); // Trial and Error
            return; // a Murky Draught carries no potion contents; nothing below applies
        }
        PotionContents contents = taken.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        contents.potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location)
                .filter(PremiumBrews::isPremium)
                .ifPresent(id -> DistillationCriteria.PREMIUM_BREWED.trigger(serverPlayer)); // The Good Stuff
        for (MobEffectInstance instance : contents.getAllEffects()) {
            String line = missingLineFor(instance.getEffect());
            if (line != null) {
                DistillationCriteria.MISSING_LINE_BREWED.trigger(serverPlayer, line); // The Missing Shelf
            }
        }
    }

    /** On a genuine first discovery: the discovery-count and full-graph milestones, against the live graph. */
    private static void onDiscover(ServerPlayer player) {
        Set<ResourceLocation> graphIds = RecipeGraphs.currentRecipeIds();
        int count = DiscoveryManager.data(player).discoveredCount(graphIds);
        DistillationCriteria.RECIPES_DISCOVERED.trigger(player, count); // Scholar of the Still (≥10)
        if (!graphIds.isEmpty() && count >= graphIds.size()) {
            DistillationCriteria.GRAPH_COMPLETED.trigger(player); // Every Drop (persists as any advancement)
        }
    }

    /** A completed batch pass is credited to its owner, when they are online to receive the grant. */
    private static void onBrew(ServerLevel level, BlockPos pos, ItemStack ingredient, List<ItemStack> results,
                               UUID batchOwner, boolean batch) {
        if (batch && batchOwner != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(batchOwner);
            if (owner != null) {
                DistillationCriteria.BATCH_COMPLETED.trigger(owner); // Round for the Table
            }
        }
    }

    /** The §2 line whose effect this holder is, or {@code null} for anything outside the five. */
    private static String missingLineFor(Holder<MobEffect> effect) {
        MobEffect value = effect.value();
        if (value == MobEffects.DAMAGE_RESISTANCE.value()) {
            return "resistance";
        }
        if (value == MobEffects.DIG_SPEED.value()) {
            return "haste";
        }
        if (value == MobEffects.ABSORPTION.value()) {
            return "absorption";
        }
        if (value == MobEffects.LUCK.value()) {
            return "luck";
        }
        if (value == MobEffects.GLOWING.value()) {
            return "glowing";
        }
        return null;
    }
}
