// Tier: 1 (pure JUnit — the copy-authorization decision table, no Fabric runtime)
package com.rfizzle.distillation.item;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipe-note copy gate of {@code design/SPEC.md} §1: a note is minted only when the feature is
 * on, the recipe is in the graph, the player has discovered it, and they hold paper — and the check
 * order fixes which reason surfaces first when several fail. Pure booleans, so the whole table sits
 * at tier 1.
 */
class RecipeNotesTest {

    @Test
    void allInputsSatisfiedAllowsTheCopy() {
        assertEquals(Optional.empty(), RecipeNotes.denial(true, true, true, true));
        assertTrue(RecipeNotes.allows(true, true, true, true));
    }

    @Test
    void featureDisabledIsReportedFirst() {
        // Disabled dominates even when every other input would also fail.
        assertEquals(Optional.of(RecipeNotes.Denial.FEATURE_DISABLED),
                RecipeNotes.denial(false, false, false, false));
        assertFalse(RecipeNotes.allows(false, true, true, true));
    }

    @Test
    void unknownRecipeReportedWhenEnabledButNotInGraph() {
        assertEquals(Optional.of(RecipeNotes.Denial.UNKNOWN_RECIPE),
                RecipeNotes.denial(true, false, true, true));
    }

    @Test
    void notDiscoveredReportedWhenInGraphButUnlearned() {
        // The stand still teaches first — a note can't be copied for a recipe the player never brewed.
        assertEquals(Optional.of(RecipeNotes.Denial.NOT_DISCOVERED),
                RecipeNotes.denial(true, true, false, true));
    }

    @Test
    void noPaperReportedLastWhenEverythingElsePasses() {
        assertEquals(Optional.of(RecipeNotes.Denial.NO_PAPER),
                RecipeNotes.denial(true, true, true, false));
        assertFalse(RecipeNotes.allows(true, true, true, false));
    }
}
