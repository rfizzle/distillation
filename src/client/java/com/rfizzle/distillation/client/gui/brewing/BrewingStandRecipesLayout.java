package com.rfizzle.distillation.client.gui.brewing;

/**
 * Pure geometry and pagination for the brewing-stand screen additions of {@code design/SPEC.md} §1
 * — the vapor-hint region, the recipes-page tab button, and the paged recipes overlay with its
 * arrows, close box, and icon rows.
 *
 * <p>Everything here is deterministic integer math with no Minecraft rendering or state, so the
 * screen mixin and its renderers read one source of truth and the whole thing unit-tests without
 * bootstrapping the client. The mixin supplies the screen-relative origins ({@code leftPos}/{@code
 * topPos}) and window size ({@code width}/{@code height}); this class turns those into positions and
 * answers whether a point falls in a region.
 */
public final class BrewingStandRecipesLayout {
    private BrewingStandRecipesLayout() {
    }

    // ---- Vanilla brewing-stand panel geometry (panel-local, origin at leftPos/topPos) ----

    /** The vanilla brewing-stand texture is 176×166 (AbstractContainerScreen defaults). */
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    public static final int SLOT_SIZE = 16;

    /** Ingredient slot (BrewingStandMenu slot 3) — the hover target for vapor hints. */
    public static final int INGREDIENT_SLOT_X = 79;
    public static final int INGREDIENT_SLOT_Y = 17;

    /** The bubble/vapor column vanilla animates while a cycle runs — where the hint tint paints. */
    public static final int VAPOR_X = 63;
    public static final int VAPOR_Y = 14;
    public static final int VAPOR_W = 12;
    public static final int VAPOR_H = 29;

    // ---- Recipes-page tab button (top-right of the panel) ----

    public static final int TAB_SIZE = 16;
    public static final int TAB_MARGIN = 6;

    // ---- Centered recipes overlay ----

    public static final int OVERLAY_WIDTH = 176;
    public static final int OVERLAY_HEIGHT = 200;
    public static final int OVERLAY_PAD = 8;
    public static final int HEADER_HEIGHT = 26;
    public static final int FOOTER_HEIGHT = 22;
    public static final int ROW_HEIGHT = 20;
    public static final int CLOSE_SIZE = 11;
    public static final int ARROW_W = 12;
    public static final int ARROW_H = 16;

    /** How many discovered-recipe rows fit on one overlay page. */
    public static final int ROWS_PER_PAGE = (OVERLAY_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT;

    // Icon-row internal x offsets (relative to the overlay's left edge): input, +, ingredient,
    // →, output. The cluster is centered in the overlay width.
    public static final int ROW_INPUT_DX = 45;
    public static final int ROW_PLUS_DX = 67;
    public static final int ROW_INGREDIENT_DX = 79;
    public static final int ROW_ARROW_DX = 101;
    public static final int ROW_OUTPUT_DX = 115;

    // ---- Tab button ----

    /** Tab button left x in screen coords: inset from the panel's right edge. */
    public static int tabX(int leftPos) {
        return leftPos + IMAGE_WIDTH - TAB_SIZE - TAB_MARGIN;
    }

    /** Tab button top y in screen coords: near the panel's top edge. */
    public static int tabY(int topPos) {
        return topPos + TAB_MARGIN - 1;
    }

    // ---- Overlay frame ----

    public static int overlayX(int screenWidth) {
        return (screenWidth - OVERLAY_WIDTH) / 2;
    }

    public static int overlayY(int screenHeight) {
        return (screenHeight - OVERLAY_HEIGHT) / 2;
    }

    /** Top y of the row band (first row's icon top) for an overlay anchored at {@code overlayTop}. */
    public static int rowTop(int overlayTop, int rowIndex) {
        return overlayTop + HEADER_HEIGHT + rowIndex * ROW_HEIGHT;
    }

    public static int closeX(int screenWidth) {
        return overlayX(screenWidth) + OVERLAY_WIDTH - CLOSE_SIZE - 6;
    }

    public static int closeY(int screenHeight) {
        return overlayY(screenHeight) + 6;
    }

    public static int prevArrowX(int screenWidth) {
        return overlayX(screenWidth) + OVERLAY_PAD;
    }

    public static int nextArrowX(int screenWidth) {
        return overlayX(screenWidth) + OVERLAY_WIDTH - OVERLAY_PAD - ARROW_W;
    }

    public static int arrowY(int screenHeight) {
        return overlayY(screenHeight) + OVERLAY_HEIGHT - FOOTER_HEIGHT + (FOOTER_HEIGHT - ARROW_H) / 2;
    }

    // ---- Pagination ----

    /** Total pages for {@code total} discovered recipes — never below one (the empty page). */
    public static int pageCount(int total) {
        if (total <= 0) {
            return 1;
        }
        return (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
    }

    /** Clamps a page index into {@code [0, pageCount-1]} — survives discovery shrinking underfoot. */
    public static int clampPage(int page, int total) {
        int last = pageCount(total) - 1;
        if (page < 0) {
            return 0;
        }
        return Math.min(page, last);
    }

    /** The absolute recipe index of the first row on {@code page}. */
    public static int firstIndexOnPage(int page) {
        return page * ROWS_PER_PAGE;
    }

    // ---- Hit-testing ----

    /** True when the point falls in the half-open rect [x, x+w) × [y, y+h). */
    public static boolean pointIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** True when the point falls anywhere inside the centered overlay frame. */
    public static boolean pointInOverlay(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        return pointIn(mouseX, mouseY, overlayX(screenWidth), overlayY(screenHeight), OVERLAY_WIDTH, OVERLAY_HEIGHT);
    }
}
