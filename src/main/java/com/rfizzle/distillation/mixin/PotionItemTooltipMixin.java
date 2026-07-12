package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.brew.PremiumBrews;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The "Concentrated" tooltip line of {@code design/SPEC.md} §5. Injected at the RETURN of
 * {@code appendHoverText} — step 2 of tooltip assembly (see {@code mc-tooltips}) — so the line sits
 * above lore, advanced info, and every recipe-viewer footer, right under the potion's effect lines.
 *
 * <p>Targets both {@link PotionItem} (the drinkable bottle, {@code Items.POTION}; splash potions
 * inherit its {@code appendHoverText} unchanged) and {@link LingeringPotionItem}, which overrides
 * {@code appendHoverText} without calling {@code super} — so the shared inject reaches every potion
 * form a Concentrated/premium bottle can take (§5.3: splash and lingering apply as to any potion).
 * The pre-existing {@code PotionItemMixin} also touches {@code PotionItem.appendHoverText}, but via
 * a {@code @ModifyArg} on an internal call — a disjoint injection point, so ordering is inert.
 *
 * <p>Keyed on the family predicate, not {@code enablePremiumBrews}, so an existing concentrated or
 * premium bottle keeps its tag with the feature off. Cheap early return for every vanilla potion.
 */
@Mixin({PotionItem.class, LingeringPotionItem.class})
abstract class PotionItemTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void distillation$concentratedTag(ItemStack stack, Item.TooltipContext context,
                                              List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        ResourceLocation id = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location)
                .orElse(null);
        if (id != null && PremiumBrews.isConcentrated(id)) {
            tooltip.add(Component.translatable("tooltip.distillation.concentrated").withStyle(ChatFormatting.GRAY));
        }
    }
}
