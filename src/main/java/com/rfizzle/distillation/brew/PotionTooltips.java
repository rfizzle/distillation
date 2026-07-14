package com.rfizzle.distillation.brew;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared tooltip lines for the potion-family marks of {@code design/SPEC.md} §5, so every item that
 * can carry a {@link PotionContents} — the bottle and its splash/lingering forms, and the tipped
 * arrow crafted from a lingering one — tags identically. The deeper liquid color already reaches all
 * of them through the item-agnostic {@code PotionContentsColorMixin}; the tag line is per-item-class
 * {@code appendHoverText} work, and this is the one place that work lives.
 */
public final class PotionTooltips {

    private PotionTooltips() {
    }

    /** The unwrapped potion id of a stack's {@link DataComponents#POTION_CONTENTS}, or null if none. */
    @Nullable
    public static ResourceLocation potionId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location)
                .orElse(null);
    }

    /**
     * Appends the "Concentrated" line when the stack carries a concentrated/premium potion. Keyed on
     * {@link PremiumBrews#isConcentrated}, not {@code enablePremiumBrews}, so an existing concentrated
     * or premium item keeps its tag with the feature off. Cheap early return for every vanilla potion.
     */
    public static void appendConcentratedTag(ItemStack stack, List<Component> tooltip) {
        ResourceLocation id = potionId(stack);
        if (id != null && PremiumBrews.isConcentrated(id)) {
            tooltip.add(Component.translatable("tooltip.distillation.concentrated").withStyle(ChatFormatting.GRAY));
        }
    }
}
