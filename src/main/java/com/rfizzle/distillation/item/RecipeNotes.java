package com.rfizzle.distillation.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The recipe-note core ({@code design/SPEC.md} §1): the pure copy-authorization decision and the
 * note-stack read/write, kept apart from the C2S shell so the rule table unit-tests without a
 * server. A note is a static reference card — it points at a recipe, it never grants one.
 */
public final class RecipeNotes {

    private RecipeNotes() {
    }

    /** Why a copy is refused; the seam that reports it decides how (silent server-side, per §1). */
    public enum Denial {
        /** {@code enableRecipeNotes=false}. */
        FEATURE_DISABLED,
        /** The recipe id is not in the live graph — nothing to copy. */
        UNKNOWN_RECIPE,
        /** The player has not discovered the recipe — the stand still teaches first. */
        NOT_DISCOVERED,
        /** The player holds no paper to copy onto. */
        NO_PAPER
    }

    /**
     * The pure authorization gate: a note may be minted only when the feature is on, the recipe is
     * in the live graph, the player has discovered it, and they hold paper. The check order fixes
     * which reason a caller sees first; the server handler re-derives every input rather than
     * trusting the client ({@code mc-networking} — never trust the client).
     */
    public static Optional<Denial> denial(boolean enabled, boolean inGraph, boolean discovered, boolean hasPaper) {
        if (!enabled) {
            return Optional.of(Denial.FEATURE_DISABLED);
        }
        if (!inGraph) {
            return Optional.of(Denial.UNKNOWN_RECIPE);
        }
        if (!discovered) {
            return Optional.of(Denial.NOT_DISCOVERED);
        }
        if (!hasPaper) {
            return Optional.of(Denial.NO_PAPER);
        }
        return Optional.empty();
    }

    /** {@code true} when {@link #denial} would allow the copy. */
    public static boolean allows(boolean enabled, boolean inGraph, boolean discovered, boolean hasPaper) {
        return denial(enabled, inGraph, discovered, hasPaper).isEmpty();
    }

    /** A recipe note pointing at {@code recipeId}. */
    public static ItemStack createNote(ResourceLocation recipeId) {
        ItemStack note = new ItemStack(DistillationItems.RECIPE_NOTE);
        note.set(DistillationItems.NOTED_RECIPE, recipeId);
        return note;
    }

    /** The recipe id a stack points at, if it is a recipe note carrying one. */
    public static Optional<ResourceLocation> notedRecipe(ItemStack stack) {
        return Optional.ofNullable(stack.get(DistillationItems.NOTED_RECIPE));
    }
}
