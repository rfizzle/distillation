package com.rfizzle.distillation.item;

import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

/**
 * A copied recipe on paper ({@code design/SPEC.md} §1): tradeable, giftable, and purely
 * informational. Its tooltip resolves the stored recipe id against the live graph and reads
 * {@code input + ingredient → output}, with a reminder that the stand still does the teaching.
 * The note never records discovery — brewing the recipe at the stand does, through the one brew
 * seam. A note whose recipe the current graph no longer carries (a datapack change since it was
 * copied) reads as unreadable rather than showing stale names.
 */
public class RecipeNoteItem extends Item {

    public RecipeNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RecipeGraph.Conversion conversion = resolve(stack);
        if (conversion == null) {
            tooltip.add(Component.translatable("tooltip.distillation.recipe_note.unknown")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.addAll(recipeTooltip(conversion));
    }

    /**
     * The two lines a resolved note shows: the {@code input + ingredient → output} recipe grammar
     * (input and output rendered as their potion or container item names, matching the recipes page)
     * and the reminder that the stand still teaches. Package-private so the branch by conversion kind
     * is unit-testable against a synthetic graph without a client tooltip context.
     */
    static List<Component> recipeTooltip(RecipeGraph.Conversion conversion) {
        return List.of(
                Component.translatable("tooltip.distillation.recipe_note.recipe",
                                inputName(conversion), conversion.ingredient().getDescription(), outputName(conversion))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.distillation.recipe_note.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The conversion this note points at, resolved against the graph a level-less client tooltip
     * can reach ({@link RecipeGraphs#tooltipGraph()}) — {@code null} for a bare note (no component)
     * or a recipe the current graph no longer carries.
     */
    private static RecipeGraph.Conversion resolve(ItemStack stack) {
        ResourceLocation recipeId = stack.get(DistillationItems.NOTED_RECIPE);
        if (recipeId == null) {
            return null;
        }
        return RecipeGraphs.tooltipGraph()
                .flatMap(graph -> graph.conversionById(recipeId))
                .orElse(null);
    }

    private static Component inputName(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.from()).getHoverName();
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).from()).getHoverName();
    }

    private static Component outputName(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.to()).getHoverName();
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).to()).getHoverName();
    }
}
