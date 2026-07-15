package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.brew.PotionTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The "Concentrated" tag ({@code design/SPEC.md} §5) on a tipped arrow. A tipped arrow crafted from a
 * Concentrated or premium lingering potion carries the family {@link net.minecraft.world.item.alchemy.PotionContents}
 * and already renders the deepened liquid color (the item-agnostic {@code PotionContentsColorMixin}),
 * but {@code PotionItemTooltipMixin} targets {@code PotionItem}/{@code LingeringPotionItem} and
 * {@code TippedArrowItem extends ArrowItem}, so the arrow never took the tag line. This closes the
 * paired-mark split on that one surface.
 *
 * <p>Injected at the RETURN of {@code appendHoverText} — step 2 of tooltip assembly (see
 * {@code mc-tooltips}), right after the arrow's effect lines and above every recipe-viewer footer,
 * matching where the potion mixin places the same line. The shared {@link PotionTooltips} helper keys
 * the tag on the concentrated/premium family, not {@code enablePremiumBrews}, so an existing arrow
 * keeps its tag with the feature off.
 */
@Mixin(TippedArrowItem.class)
abstract class TippedArrowTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void distillation$concentratedTag(ItemStack stack, Item.TooltipContext context,
                                              List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        PotionTooltips.appendConcentratedTag(stack, tooltip);
    }
}
