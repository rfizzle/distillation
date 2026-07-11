package com.rfizzle.distillation.client.gui.brewing;

import com.rfizzle.distillation.recipe.RecipeGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The in-stand recipes page of {@code design/SPEC.md} §1: a centered overlay listing every recipe
 * the player has discovered as an {@code input + ingredient → output} icon row, in discovery order,
 * paged, with a running count that gilds at full discovery.
 *
 * <p>Draws entirely through {@link GuiGraphics} inside the screen's render frame (mc-screen). All
 * geometry comes from {@link BrewingStandRecipesLayout}; the discovered set and graph are supplied
 * by the mixin from client-resident state.
 */
public final class RecipesPageRenderer {

    // Lang keys — mirrored by RecipesPageLangContractTest so a used key can't ship missing.
    public static final String KEY_BUTTON = "gui.distillation.recipes_page.button";
    public static final String KEY_TITLE = "gui.distillation.recipes_page.title";
    public static final String KEY_COUNT = "gui.distillation.recipes_page.count";
    public static final String KEY_COUNT_FULL = "gui.distillation.recipes_page.count.full";
    public static final String KEY_EMPTY = "gui.distillation.recipes_page.empty";
    public static final String KEY_PAGE = "gui.distillation.recipes_page.page";

    private static final int DIM_COLOR = 0xC0000000;
    private static final int PANEL_BG = 0xF11A0A18;
    private static final int PANEL_BORDER = 0xFFC44DCC;
    private static final int TITLE_COLOR = 0xFFDA79E3;
    private static final int COUNT_COLOR = 0xFFCFC9D4;
    private static final int COUNT_FULL_COLOR = 0xFFFFD966;
    private static final int SYMBOL_COLOR = 0xFF9A8FA0;
    private static final int EMPTY_COLOR = 0xFF9A8FA0;
    private static final int ARROW_COLOR = 0xFFEDE7F0;
    private static final int ARROW_DISABLED = 0xFF5E5548;

    // The three display stacks per conversion, cached by conversion identity so an open overlay
    // doesn't rebuild them every frame. Conversions are immutable and graph-scoped; a weak key lets
    // an entry die with its graph on reload. Render-thread only, so no synchronization.
    private static final Map<RecipeGraph.Conversion, ItemStack[]> ROW_STACKS = new WeakHashMap<>();

    private RecipesPageRenderer() {
    }

    /**
     * The discovered conversions that still resolve against the current graph, in discovery order —
     * the exact list the page renders and pages over. Mirrors {@code DiscoveryData.discoveredCount}'s
     * "hide entries stale against the graph" rule on the client's raw id set.
     */
    public static List<RecipeGraph.Conversion> visibleConversions(RecipeGraph graph,
                                                                  Set<ResourceLocation> discovered) {
        List<RecipeGraph.Conversion> visible = new ArrayList<>();
        for (ResourceLocation id : discovered) {
            graph.conversionById(id).ifPresent(visible::add);
        }
        return visible;
    }

    /**
     * Draws the overlay: dim, frame, title, running count, the current page's rows, page arrows and
     * indicator, and the close box. {@code total} is the full graph size (the count denominator);
     * {@code visible} is the ordered discovered list.
     */
    public static void render(GuiGraphics guiGraphics, Font font, List<RecipeGraph.Conversion> visible,
                              int total, int page, int screenWidth, int screenHeight,
                              int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, screenWidth, screenHeight, DIM_COLOR);

        int ox = BrewingStandRecipesLayout.overlayX(screenWidth);
        int oy = BrewingStandRecipesLayout.overlayY(screenHeight);
        int w = BrewingStandRecipesLayout.OVERLAY_WIDTH;
        int h = BrewingStandRecipesLayout.OVERLAY_HEIGHT;

        guiGraphics.fill(ox - 1, oy - 1, ox + w + 1, oy + h + 1, PANEL_BORDER);
        guiGraphics.fill(ox, oy, ox + w, oy + h, PANEL_BG);

        // Header: title and running count.
        Component title = Component.translatable(KEY_TITLE);
        guiGraphics.drawString(font, title, ox + BrewingStandRecipesLayout.OVERLAY_PAD, oy + 9, TITLE_COLOR, false);

        int discoveredCount = visible.size();
        boolean full = total > 0 && discoveredCount == total;
        Component count = full
                ? Component.translatable(KEY_COUNT_FULL, discoveredCount, total)
                : Component.translatable(KEY_COUNT, discoveredCount, total);
        int countColor = full ? COUNT_FULL_COLOR : COUNT_COLOR;
        int countX = ox + w - BrewingStandRecipesLayout.OVERLAY_PAD - BrewingStandRecipesLayout.CLOSE_SIZE
                - 2 - font.width(count);
        guiGraphics.drawString(font, count, countX, oy + 9, countColor, false);

        // Rows for the current page, or the empty state.
        if (visible.isEmpty()) {
            Component empty = Component.translatable(KEY_EMPTY);
            guiGraphics.drawString(font, empty, ox + (w - font.width(empty)) / 2, oy + h / 2 - 4, EMPTY_COLOR, false);
        } else {
            int first = BrewingStandRecipesLayout.firstIndexOnPage(page);
            for (int row = 0; row < BrewingStandRecipesLayout.ROWS_PER_PAGE; row++) {
                int index = first + row;
                if (index >= visible.size()) {
                    break;
                }
                RecipeGraph.Conversion conversion = visible.get(index);
                int rowTop = BrewingStandRecipesLayout.rowTop(oy, row);
                ItemStack[] stacks = rowStacks(conversion);
                guiGraphics.renderItem(stacks[0], ox + BrewingStandRecipesLayout.ROW_INPUT_DX, rowTop);
                drawSymbol(guiGraphics, font, "+", ox + BrewingStandRecipesLayout.ROW_PLUS_DX, rowTop);
                guiGraphics.renderItem(stacks[1], ox + BrewingStandRecipesLayout.ROW_INGREDIENT_DX, rowTop);
                drawSymbol(guiGraphics, font, "→", ox + BrewingStandRecipesLayout.ROW_ARROW_DX, rowTop);
                guiGraphics.renderItem(stacks[2], ox + BrewingStandRecipesLayout.ROW_OUTPUT_DX, rowTop);
            }
        }

        // Footer: page arrows and indicator (only when paging is possible).
        int pageCount = BrewingStandRecipesLayout.pageCount(visible.size());
        if (pageCount > 1) {
            int arrowY = BrewingStandRecipesLayout.arrowY(screenHeight);
            int prevX = BrewingStandRecipesLayout.prevArrowX(screenWidth);
            int nextX = BrewingStandRecipesLayout.nextArrowX(screenWidth);
            drawArrow(guiGraphics, font, "◀", prevX, arrowY, page > 0,
                    BrewingStandRecipesLayout.pointIn(mouseX, mouseY, prevX, arrowY,
                            BrewingStandRecipesLayout.ARROW_W, BrewingStandRecipesLayout.ARROW_H));
            drawArrow(guiGraphics, font, "▶", nextX, arrowY, page < pageCount - 1,
                    BrewingStandRecipesLayout.pointIn(mouseX, mouseY, nextX, arrowY,
                            BrewingStandRecipesLayout.ARROW_W, BrewingStandRecipesLayout.ARROW_H));
            Component indicator = Component.translatable(KEY_PAGE, page + 1, pageCount);
            guiGraphics.drawString(font, indicator, ox + (w - font.width(indicator)) / 2, arrowY + 4, COUNT_COLOR, false);
        }

        // Close box (top-right).
        int closeX = BrewingStandRecipesLayout.closeX(screenWidth);
        int closeY = BrewingStandRecipesLayout.closeY(screenHeight);
        boolean closeHover = BrewingStandRecipesLayout.pointIn(mouseX, mouseY, closeX, closeY,
                BrewingStandRecipesLayout.CLOSE_SIZE, BrewingStandRecipesLayout.CLOSE_SIZE);
        guiGraphics.drawString(font, Component.literal("✕"), closeX + 2, closeY + 1,
                closeHover ? 0xFFFFFFFF : COUNT_COLOR, false);
    }

    /**
     * The row stack under the cursor (input, ingredient, or output icon), or {@link ItemStack#EMPTY}
     * — so the mixin can show a vanilla item tooltip for it. Only meaningful while the overlay is
     * open with a non-empty list.
     */
    public static ItemStack stackUnderMouse(List<RecipeGraph.Conversion> visible, int page,
                                            int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int ox = BrewingStandRecipesLayout.overlayX(screenWidth);
        int oy = BrewingStandRecipesLayout.overlayY(screenHeight);
        int first = BrewingStandRecipesLayout.firstIndexOnPage(page);
        for (int row = 0; row < BrewingStandRecipesLayout.ROWS_PER_PAGE; row++) {
            int index = first + row;
            if (index >= visible.size()) {
                break;
            }
            int rowTop = BrewingStandRecipesLayout.rowTop(oy, row);
            ItemStack[] stacks = rowStacks(visible.get(index));
            if (overIcon(mouseX, mouseY, ox + BrewingStandRecipesLayout.ROW_INPUT_DX, rowTop)) {
                return stacks[0];
            }
            if (overIcon(mouseX, mouseY, ox + BrewingStandRecipesLayout.ROW_INGREDIENT_DX, rowTop)) {
                return stacks[1];
            }
            if (overIcon(mouseX, mouseY, ox + BrewingStandRecipesLayout.ROW_OUTPUT_DX, rowTop)) {
                return stacks[2];
            }
        }
        return ItemStack.EMPTY;
    }

    /** The {@code [input, ingredient, output]} display stacks for a conversion, cached by identity. */
    private static ItemStack[] rowStacks(RecipeGraph.Conversion conversion) {
        return ROW_STACKS.computeIfAbsent(conversion,
                c -> new ItemStack[]{inputStack(c), ingredientStack(c), outputStack(c)});
    }

    private static boolean overIcon(int mouseX, int mouseY, int x, int y) {
        return BrewingStandRecipesLayout.pointIn(mouseX, mouseY, x, y,
                BrewingStandRecipesLayout.SLOT_SIZE, BrewingStandRecipesLayout.SLOT_SIZE);
    }

    private static void drawSymbol(GuiGraphics guiGraphics, Font font, String symbol, int x, int rowTop) {
        guiGraphics.drawString(font, symbol, x, rowTop + (BrewingStandRecipesLayout.SLOT_SIZE - font.lineHeight) / 2 + 4,
                SYMBOL_COLOR, false);
    }

    private static void drawArrow(GuiGraphics guiGraphics, Font font, String glyph, int x, int y,
                                  boolean enabled, boolean hover) {
        int color = enabled ? (hover ? 0xFFFFFFFF : ARROW_COLOR) : ARROW_DISABLED;
        guiGraphics.drawString(font, glyph, x + 2, y + 4, color, false);
    }

    private static ItemStack inputStack(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.from());
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).from());
    }

    private static ItemStack ingredientStack(RecipeGraph.Conversion conversion) {
        return new ItemStack(conversion.ingredient());
    }

    private static ItemStack outputStack(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.to());
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).to());
    }
}
