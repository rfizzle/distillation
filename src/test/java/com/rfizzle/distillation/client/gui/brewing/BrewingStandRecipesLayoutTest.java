package com.rfizzle.distillation.client.gui.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the pure geometry and pagination of the brewing-stand recipe surfaces. No Minecraft
 * bootstrap: the mixin feeds screen origins in, this class answers positions and page math, and
 * the two must agree, so the button, overlay, and hover regions can't drift apart.
 */
class BrewingStandRecipesLayoutTest {

    @Test
    void rowsPerPageMatchesTheContentBand() {
        // (200 - 26 header - 22 footer) / 20 per row = 7.
        assertEquals(7, BrewingStandRecipesLayout.ROWS_PER_PAGE);
    }

    @Test
    void pageCountRoundsUpAndNeverDropsBelowOne() {
        assertEquals(1, BrewingStandRecipesLayout.pageCount(0));
        assertEquals(1, BrewingStandRecipesLayout.pageCount(7));
        assertEquals(2, BrewingStandRecipesLayout.pageCount(8));
        assertEquals(2, BrewingStandRecipesLayout.pageCount(14));
        assertEquals(3, BrewingStandRecipesLayout.pageCount(15));
        assertEquals(9, BrewingStandRecipesLayout.pageCount(61));
    }

    @Test
    void clampPageStaysInBounds() {
        // 8 recipes → 2 pages → last index 1.
        assertEquals(1, BrewingStandRecipesLayout.clampPage(5, 8));
        assertEquals(0, BrewingStandRecipesLayout.clampPage(-3, 61));
        assertEquals(3, BrewingStandRecipesLayout.clampPage(3, 61));
        // Discovery shrank under an open page: page 4 with only 8 recipes clamps back to the last.
        assertEquals(1, BrewingStandRecipesLayout.clampPage(4, 8));
    }

    @Test
    void firstIndexOnPageStepsByPageSize() {
        assertEquals(0, BrewingStandRecipesLayout.firstIndexOnPage(0));
        assertEquals(7, BrewingStandRecipesLayout.firstIndexOnPage(1));
        assertEquals(14, BrewingStandRecipesLayout.firstIndexOnPage(2));
    }

    @Test
    void overlayCentersOnTheWindow() {
        assertEquals(112, BrewingStandRecipesLayout.overlayX(400));
        assertEquals(50, BrewingStandRecipesLayout.overlayY(300));
    }

    @Test
    void tabSitsTopRightOfThePanel() {
        // leftPos + 176 - 16 - 6.
        assertEquals(254, BrewingStandRecipesLayout.tabX(100));
        assertEquals(55, BrewingStandRecipesLayout.tabY(50));
    }

    @Test
    void rowTopStacksBelowTheHeader() {
        assertEquals(76, BrewingStandRecipesLayout.rowTop(50, 0));
        assertEquals(136, BrewingStandRecipesLayout.rowTop(50, 3));
    }

    @Test
    void footerControlsAnchorToTheOverlay() {
        assertEquals(271, BrewingStandRecipesLayout.closeX(400));
        assertEquals(56, BrewingStandRecipesLayout.closeY(300));
        assertEquals(120, BrewingStandRecipesLayout.prevArrowX(400));
        assertEquals(268, BrewingStandRecipesLayout.nextArrowX(400));
        assertEquals(231, BrewingStandRecipesLayout.arrowY(300));
    }

    @Test
    void pointInIsHalfOpen() {
        assertTrue(BrewingStandRecipesLayout.pointIn(10, 10, 10, 10, 16, 16));
        assertTrue(BrewingStandRecipesLayout.pointIn(25, 25, 10, 10, 16, 16));
        assertFalse(BrewingStandRecipesLayout.pointIn(26, 10, 10, 10, 16, 16));
        assertFalse(BrewingStandRecipesLayout.pointIn(9, 10, 10, 10, 16, 16));
    }

    @Test
    void pointInOverlayCoversTheWholeFrame() {
        // overlay at (112, 50), 176×200.
        assertTrue(BrewingStandRecipesLayout.pointInOverlay(112, 50, 400, 300));
        assertTrue(BrewingStandRecipesLayout.pointInOverlay(200, 150, 400, 300));
        assertFalse(BrewingStandRecipesLayout.pointInOverlay(111, 50, 400, 300));
        assertFalse(BrewingStandRecipesLayout.pointInOverlay(288, 250, 400, 300));
    }
}
