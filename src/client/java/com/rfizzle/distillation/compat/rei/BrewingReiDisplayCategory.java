package com.rfizzle.distillation.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The REI brewing category. REI has no safe programmatic reload, so it is deliberately left out of
 * the runtime-refresh dispatcher — it reads the shared snapshot whenever it next (re)builds its list
 * (on join, {@code /reload}, or F3+T), which is correct on first join, the common case.
 */
public class BrewingReiDisplayCategory implements DisplayCategory<BrewingReiDisplay> {

    private static final int SLOT_Y = 5;

    @Override
    public CategoryIdentifier<? extends BrewingReiDisplay> getCategoryIdentifier() {
        return BrewingReiDisplay.IDENTIFIER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rei.distillation.category.brewing");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(new ItemStack(Items.BREWING_STAND));
    }

    @Override
    public int getDisplayWidth(BrewingReiDisplay display) {
        return 108;
    }

    @Override
    public int getDisplayHeight() {
        return 30;
    }

    @Override
    public List<Widget> setupDisplay(BrewingReiDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        int x = bounds.x;
        int y = bounds.y;
        widgets.add(Widgets.createSlot(new Point(x + 4, y + SLOT_Y))
                .entries(display.getInputEntries().get(0)).markInput());
        widgets.add(Widgets.createSlot(new Point(x + 26, y + SLOT_Y))
                .entries(display.getInputEntries().get(1)).markInput());
        widgets.add(Widgets.createArrow(new Point(x + 50, y + SLOT_Y - 1)));
        widgets.add(Widgets.createSlot(new Point(x + 82, y + SLOT_Y))
                .entries(display.getOutputEntries().get(0)).markOutput());
        return widgets;
    }
}
