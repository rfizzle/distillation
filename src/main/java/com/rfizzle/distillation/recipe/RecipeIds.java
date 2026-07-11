package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.Optional;

/**
 * Stable recipe-id derivation per {@code design/SPEC.md} §1: every conversion is
 * {@code distillation:<ingredient segment>/<input segment>}, where a segment is the bare path for
 * the {@code minecraft} namespace and {@code <namespace>/<path>} otherwise — e.g.
 * {@code distillation:shulker_shell/awkward}, {@code distillation:redstone/distillation/haste}.
 * Container conversions use the input container item as their input segment
 * ({@code distillation:gunpowder/potion}).
 *
 * <p>Ids are opaque keys (discovery sets, commands, the API surface); nothing parses them back.
 */
public final class RecipeIds {

    private RecipeIds() {
    }

    public static ResourceLocation derive(ResourceLocation ingredientItem, ResourceLocation input) {
        return Distillation.id(segment(ingredientItem) + "/" + segment(input));
    }

    /**
     * The id of a potion conversion, from live objects — the one derivation both the graph builder
     * and the config exclusion set ({@code DistillationBrews.ownedRecipeIds}) use, so the two can
     * never drift apart. Empty when the input holder carries no registry key (no stable id exists).
     */
    public static Optional<ResourceLocation> forPotionInput(Item ingredient, Holder<Potion> input) {
        return input.unwrapKey()
                .map(key -> derive(BuiltInRegistries.ITEM.getKey(ingredient), key.location()));
    }

    private static String segment(ResourceLocation location) {
        return ResourceLocation.DEFAULT_NAMESPACE.equals(location.getNamespace())
                ? location.getPath()
                : location.getNamespace() + "/" + location.getPath();
    }
}
