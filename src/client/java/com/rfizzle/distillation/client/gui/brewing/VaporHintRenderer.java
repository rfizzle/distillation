package com.rfizzle.distillation.client.gui.brewing;

import com.rfizzle.distillation.recipe.RecipeGraph;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The vapor hint of {@code design/SPEC.md} §1: while a held ingredient hovers the brewing stand's
 * ingredient slot, the bubble/vapor column tints with the color of what it would brew against the
 * bottles below, and — once every valid pair is already discovered — a tooltip names the output(s).
 *
 * <p>Resolution reads only client-resident state (the recipe graph, the bottle stacks, the synced
 * discovery set), so it runs entirely in the screen's render pass with no server round-trip. The
 * blend and opacity math lives in {@link VaporHintColors}; this class turns a graph resolution into
 * a tint draw and the tooltip lines.
 */
public final class VaporHintRenderer {

    // Vanilla's own bubble sprite, tinted in code — no custom texture (design/DESIGN.md §3).
    private static final ResourceLocation BUBBLES_SPRITE =
            ResourceLocation.withDefaultNamespace("container/brewing_stand/bubbles");

    // Vanilla BrewingStandScreen.BUBBLELENGTHS — the rising-bubble frame heights the stand cycles
    // through. Reused so the hint's vapor animates exactly like a live brew's, driven off a
    // free-running client tick (there is no brew cycle to source progress from).
    private static final int[] BUBBLE_HEIGHTS = {29, 24, 20, 16, 11, 6, 0};

    private VaporHintRenderer() {
    }

    /**
     * The resolution of a held ingredient against the three bottle slots: the distinct output
     * colors to blend, whether any pair was valid at all, whether every valid pair is already
     * discovered (the tooltip gate), and the distinct output names for that tooltip.
     */
    public record HintResult(int[] colors, boolean anyValid, boolean allDiscovered,
                             List<Component> outputNames) {
        public static final HintResult NONE = new HintResult(new int[0], false, false, List.of());
    }

    /**
     * Resolves {@code carried} against each bottle. Returns {@link HintResult#NONE} when the held
     * item is not a graph ingredient or no bottle forms a valid pair — the renderer then paints
     * nothing.
     */
    public static HintResult resolve(RecipeGraph graph, ItemStack carried, List<ItemStack> bottles,
                                     Set<ResourceLocation> discovered) {
        if (carried.isEmpty() || !graph.isIngredient(carried)) {
            return HintResult.NONE;
        }
        // Dedupe distinct outputs while preserving encounter order (bottle slot order).
        LinkedHashSet<Integer> colors = new LinkedHashSet<>();
        LinkedHashMap<String, Component> names = new LinkedHashMap<>();
        boolean anyValid = false;
        boolean allDiscovered = true;
        for (ItemStack bottle : bottles) {
            RecipeGraph.Conversion conversion = graph.matchConversion(carried, bottle);
            if (conversion == null) {
                continue;
            }
            anyValid = true;
            ItemStack output = graph.outputOf(conversion, bottle);
            int color = output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor() & 0xFFFFFF;
            colors.add(color);
            Component name = output.getHoverName();
            names.putIfAbsent(name.getString(), name);
            if (!discovered.contains(conversion.id())) {
                allDiscovered = false;
            }
        }
        if (!anyValid) {
            return HintResult.NONE;
        }
        int[] colorArray = new int[colors.size()];
        int i = 0;
        for (int color : colors) {
            colorArray[i++] = color;
        }
        return new HintResult(colorArray, true, allDiscovered, List.copyOf(names.values()));
    }

    /**
     * Paints the blended tint over the vapor column at 60% opacity (§1). The bubbles rise on a
     * free-running loop keyed by {@code animTick} (client game time), echoing vanilla's brewing
     * animation — the same partial-sprite draw the stand uses for a live cycle. On the frame the
     * loop pops to zero height nothing draws, exactly as vanilla's bubbles pop.
     */
    public static void renderTint(GuiGraphics guiGraphics, int leftPos, int topPos, HintResult hint,
                                  long animTick) {
        if (!hint.anyValid()) {
            return;
        }
        int height = BUBBLE_HEIGHTS[(int) (animTick / 2 % BUBBLE_HEIGHTS.length)];
        if (height <= 0) {
            return;
        }
        int blended = VaporHintColors.blend(hint.colors());
        guiGraphics.setColor(VaporHintColors.red(blended), VaporHintColors.green(blended),
                VaporHintColors.blue(blended), VaporHintColors.HINT_OPACITY);
        guiGraphics.blitSprite(BUBBLES_SPRITE,
                BrewingStandRecipesLayout.VAPOR_W, BrewingStandRecipesLayout.VAPOR_H,
                0, BrewingStandRecipesLayout.VAPOR_H - height,
                leftPos + BrewingStandRecipesLayout.VAPOR_X,
                topPos + BrewingStandRecipesLayout.VAPOR_Y + BrewingStandRecipesLayout.VAPOR_H - height,
                BrewingStandRecipesLayout.VAPOR_W, height);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
