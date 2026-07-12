package com.rfizzle.distillation.api;

import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Set;

/**
 * Distillation's stable public API (Concord API Standard) — the one surface siblings and
 * third-party mods build against. Reads are server-authoritative and never mutate mod state; the
 * single sanctioned mutation is {@link #registerAntidote}, an init-time additive registration.
 *
 * <p>Everything outside {@code com.rfizzle.distillation.api} is internal and may change without
 * notice. The two brew/discovery observation events live beside this facade
 * ({@link DistillationBrewCallback}, {@link DistillationDiscoveryCallback}).
 */
@Stable
public final class DistillationAPI {

    private DistillationAPI() {
    }

    /** Whether {@code player} has discovered the recipe {@code recipeId} ({@code design/SPEC.md} §1). */
    public static boolean isDiscovered(ServerPlayer player, ResourceLocation recipeId) {
        return DiscoveryManager.data(player).contains(recipeId);
    }

    /** An immutable copy of the player's discovery set — every recipe id they have discovered. */
    public static Set<ResourceLocation> getDiscoveredRecipes(ServerPlayer player) {
        return Set.copyOf(DiscoveryManager.data(player).orderedIds());
    }

    /**
     * The current recipe graph's ids, immutable and server-authoritative — every conversion the
     * stand can brew right now, including the §2/§5/§6 lines. An empty set when no server is running.
     */
    public static Set<ResourceLocation> getRecipeIds() {
        return RecipeGraphs.currentRecipeIds();
    }

    /**
     * The sanctioned additive-registration point ({@code design/SPEC.md} §6): adds a Thick-based
     * antidote line that cures {@code effectId}, brewed from {@code reagent}. Returns {@code false}
     * and changes nothing when the effect already has an antidote (or names no registered effect).
     * Callable during mod init only; the recipe graph builds after all registrations, so a line
     * added from any mod's {@code onInitialize} participates in discovery, hints, and batching like
     * a native recipe.
     */
    public static boolean registerAntidote(ResourceLocation effectId, Ingredient reagent) {
        return Antidotes.registerAntidote(effectId, reagent);
    }
}
