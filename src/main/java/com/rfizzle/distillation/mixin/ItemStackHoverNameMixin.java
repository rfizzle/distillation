package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.item.DistillationItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The half-draught name suffix ({@code design/SPEC.md} §4): a stack carrying the draught marker reads
 * "&lt;name&gt; (Half)". Guarded on the component's presence — only a sipped potion ever carries it —
 * so every other item's name is returned untouched.
 */
@Mixin(ItemStack.class)
abstract class ItemStackHoverNameMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void distillation$halfName(CallbackInfoReturnable<Component> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.has(DistillationItems.DRAUGHT)) {
            cir.setReturnValue(Component.translatable("item.distillation.draught.half", cir.getReturnValue()));
        }
    }
}
