package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.brew.PotionTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TooltipFlag;
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
 *
 * <p>An antidote ({@code design/SPEC.md} §6) instead replaces its tooltip body with a single "Cures
 * X" line naming the affliction it strips: the shared cleanse effect is an internal encoding, not
 * something to surface, so a HEAD inject cancels vanilla's effect-line assembly for antidotes and
 * writes the cure line in its place.
 */
@Mixin({PotionItem.class, LingeringPotionItem.class})
abstract class PotionItemTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void distillation$antidoteCureLine(ItemStack stack, Item.TooltipContext context,
                                               List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        ResourceLocation id = PotionTooltips.potionId(stack);
        if (id == null) {
            return;
        }
        Holder<MobEffect> cured = Antidotes.targetForPotion(id);
        if (cured != null) {
            tooltip.add(Component.translatable("tooltip.distillation.antidote", cured.value().getDisplayName())
                    .withStyle(ChatFormatting.BLUE));
            ci.cancel(); // the cure line is the whole story; suppress the raw cleanse effect line
        }
    }

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void distillation$concentratedTag(ItemStack stack, Item.TooltipContext context,
                                              List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        PotionTooltips.appendConcentratedTag(stack, tooltip);
    }
}
