package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.RiggedMenu;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.client.gui.brewing.BatchRowRenderer;
import com.rfizzle.distillation.client.gui.brewing.BrewingStandRecipesLayout;
import com.rfizzle.distillation.client.gui.brewing.RecipesPageRenderer;
import com.rfizzle.distillation.client.gui.brewing.VaporHintRenderer;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Extends vanilla's {@link BrewingStandScreen} in place with the recipe-discovery surfaces of
 * {@code design/SPEC.md} §1: the vapor-hint tint over the bubble column, the output-name tooltip
 * once every pair is discovered, the recipes-page tab button, and the paged recipes overlay.
 *
 * <p>No new {@code Screen} is registered. The mixin stays thin — it owns only the screen-coupled
 * state (the overlay-open flag and current page) and forwards each draw to a feature class that
 * holds the geometry and rendering: {@link BrewingStandRecipesLayout} for math, {@link
 * VaporHintRenderer} for the tint, {@link RecipesPageRenderer} for the overlay. Every gate is read
 * fresh per frame so a config change or discovery resync while the screen is open takes effect at
 * once: {@code enableDiscovery} (server-synced, kills button/page/hints) via {@link
 * RecipeGraphs#effectiveConfig()}, {@code showVaporHints} (client-local, kills only the hints) via
 * the local config.
 */
@Mixin(BrewingStandScreen.class)
public abstract class BrewingStandScreenMixin extends AbstractContainerScreen<BrewingStandMenu> {

    @Unique
    private static final ResourceLocation DISTILLATION$TAB_SPRITE =
            Distillation.id("textures/gui/recipes_tab.png");

    @Unique
    private static final int DISTILLATION$TAB_HOVER = 0x30FFFFFF;

    @Unique
    private boolean distillation$overlayOpen;

    @Unique
    private int distillation$page;

    private BrewingStandScreenMixin(BrewingStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // The batch row (SPEC §3) draws in the background pass so its slot cells sit behind the bottles
    // the menu's slots render. Shown only while the stand is rigged, independent of discovery.
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void distillation$renderBatchRow(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY,
                                             CallbackInfo ci) {
        if (((RiggedMenu) this.menu).distillation$isRigged()) {
            BatchRowRenderer.render(guiGraphics, this.leftPos, this.topPos, distillation$animTick());
        }
    }

    // Single render-TAIL entry point for every discovery surface. Draw order is code order, so the
    // z-order is explicit and local: tint, then tab, then the modal overlay, then tooltips last so
    // they sit on top. Never split these across separate render-TAIL injects (see the mc-screen skill).
    @Inject(method = "render", at = @At("TAIL"))
    private void distillation$renderDiscovery(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        if (!distillation$discoveryEnabled()) {
            distillation$overlayOpen = false;
            return;
        }
        RecipeGraph graph = distillation$graph();
        if (graph == null) {
            return;
        }

        // Vapor hint — resolved only when it could actually show (not under the overlay, hovering
        // the ingredient slot, hints on), so overlay-open frames skip the graph resolution entirely.
        VaporHintRenderer.HintResult hint = VaporHintRenderer.HintResult.NONE;
        boolean showHint = false;
        if (!distillation$overlayOpen && distillation$vaporHintsEnabled()
                && distillation$overIngredientSlot(mouseX, mouseY)) {
            hint = distillation$hint(graph);
            showHint = hint.anyValid();
        }
        if (showHint) {
            VaporHintRenderer.renderTint(guiGraphics, this.leftPos, this.topPos, hint, distillation$animTick());
        }

        distillation$renderTab(guiGraphics, mouseX, mouseY);

        List<RecipeGraph.Conversion> visible = null;
        if (distillation$overlayOpen) {
            visible = RecipesPageRenderer.visibleConversions(graph, ClientDiscoveryState.discovered());
            distillation$page = BrewingStandRecipesLayout.clampPage(distillation$page, visible.size());
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
            RecipesPageRenderer.render(guiGraphics, this.font, visible, graph.ids().size(),
                    distillation$page, this.width, this.height, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        // Tooltips last.
        if (distillation$overlayOpen) {
            ItemStack row = RecipesPageRenderer.stackUnderMouse(visible, distillation$page,
                    this.width, this.height, mouseX, mouseY);
            if (!row.isEmpty()) {
                guiGraphics.renderTooltip(this.font, row, mouseX, mouseY);
            }
        } else if (distillation$overTab(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable(RecipesPageRenderer.KEY_BUTTON), mouseX, mouseY);
        } else if (showHint && hint.allDiscovered()) {
            guiGraphics.renderComponentTooltip(this.font, hint.outputNames(), mouseX, mouseY);
        }
    }

    @Unique
    private void distillation$renderTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int tx = BrewingStandRecipesLayout.tabX(this.leftPos);
        int ty = BrewingStandRecipesLayout.tabY(this.topPos);
        guiGraphics.blit(DISTILLATION$TAB_SPRITE, tx, ty, 0.0F, 0.0F,
                BrewingStandRecipesLayout.TAB_SIZE, BrewingStandRecipesLayout.TAB_SIZE,
                BrewingStandRecipesLayout.TAB_SIZE, BrewingStandRecipesLayout.TAB_SIZE);
        if (!distillation$overlayOpen && distillation$overTab(mouseX, mouseY)) {
            guiGraphics.fill(tx, ty, tx + BrewingStandRecipesLayout.TAB_SIZE,
                    ty + BrewingStandRecipesLayout.TAB_SIZE, DISTILLATION$TAB_HOVER);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (distillation$discoveryEnabled()) {
            if (distillation$overlayOpen) {
                if (button == 0) {
                    if (distillation$pointIn(mouseX, mouseY, BrewingStandRecipesLayout.closeX(this.width),
                            BrewingStandRecipesLayout.closeY(this.height),
                            BrewingStandRecipesLayout.CLOSE_SIZE, BrewingStandRecipesLayout.CLOSE_SIZE)) {
                        distillation$overlayOpen = false;
                        return true;
                    }
                    int pageCount = BrewingStandRecipesLayout.pageCount(distillation$visibleCount());
                    if (distillation$page > 0 && distillation$pointIn(mouseX, mouseY,
                            BrewingStandRecipesLayout.prevArrowX(this.width),
                            BrewingStandRecipesLayout.arrowY(this.height),
                            BrewingStandRecipesLayout.ARROW_W, BrewingStandRecipesLayout.ARROW_H)) {
                        distillation$page--;
                        return true;
                    }
                    if (distillation$page < pageCount - 1 && distillation$pointIn(mouseX, mouseY,
                            BrewingStandRecipesLayout.nextArrowX(this.width),
                            BrewingStandRecipesLayout.arrowY(this.height),
                            BrewingStandRecipesLayout.ARROW_W, BrewingStandRecipesLayout.ARROW_H)) {
                        distillation$page++;
                        return true;
                    }
                    if (!BrewingStandRecipesLayout.pointInOverlay(mouseX, mouseY, this.width, this.height)) {
                        distillation$overlayOpen = false;
                        return true;
                    }
                }
                return true;
            }
            if (button == 0 && distillation$overTab(mouseX, mouseY)) {
                distillation$overlayOpen = true;
                distillation$page = BrewingStandRecipesLayout.clampPage(distillation$page, distillation$visibleCount());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (distillation$discoveryEnabled() && distillation$overlayOpen) {
            int pageCount = BrewingStandRecipesLayout.pageCount(distillation$visibleCount());
            if (scrollY < 0 && distillation$page < pageCount - 1) {
                distillation$page++;
            } else if (scrollY > 0 && distillation$page > 0) {
                distillation$page--;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (distillation$discoveryEnabled() && distillation$overlayOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            distillation$overlayOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- Gates & state (read fresh per call) ----

    @Unique
    private boolean distillation$discoveryEnabled() {
        return RecipeGraphs.effectiveConfig().enableDiscovery;
    }

    @Unique
    private boolean distillation$vaporHintsEnabled() {
        return Distillation.getConfig().client.showVaporHints;
    }

    @Unique
    private RecipeGraph distillation$graph() {
        return this.minecraft == null || this.minecraft.level == null
                ? null : RecipeGraphs.forLevel(this.minecraft.level);
    }

    @Unique
    private long distillation$animTick() {
        return this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getGameTime() : 0L;
    }

    @Unique
    private VaporHintRenderer.HintResult distillation$hint(RecipeGraph graph) {
        List<ItemStack> bottles = List.of(
                this.menu.getSlot(0).getItem(),
                this.menu.getSlot(1).getItem(),
                this.menu.getSlot(2).getItem());
        return VaporHintRenderer.resolve(graph, this.menu.getCarried(), bottles,
                ClientDiscoveryState.discovered());
    }

    @Unique
    private int distillation$visibleCount() {
        RecipeGraph graph = distillation$graph();
        return graph == null ? 0
                : RecipesPageRenderer.visibleConversions(graph, ClientDiscoveryState.discovered()).size();
    }

    // ---- Hit-testing ----

    @Unique
    private boolean distillation$overTab(double mouseX, double mouseY) {
        return distillation$pointIn(mouseX, mouseY, BrewingStandRecipesLayout.tabX(this.leftPos),
                BrewingStandRecipesLayout.tabY(this.topPos),
                BrewingStandRecipesLayout.TAB_SIZE, BrewingStandRecipesLayout.TAB_SIZE);
    }

    @Unique
    private boolean distillation$overIngredientSlot(double mouseX, double mouseY) {
        return distillation$pointIn(mouseX, mouseY,
                this.leftPos + BrewingStandRecipesLayout.INGREDIENT_SLOT_X,
                this.topPos + BrewingStandRecipesLayout.INGREDIENT_SLOT_Y,
                BrewingStandRecipesLayout.SLOT_SIZE, BrewingStandRecipesLayout.SLOT_SIZE);
    }

    @Unique
    private boolean distillation$pointIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return BrewingStandRecipesLayout.pointIn(mouseX, mouseY, x, y, w, h);
    }
}
