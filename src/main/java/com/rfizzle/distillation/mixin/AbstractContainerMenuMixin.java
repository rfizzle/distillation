package com.rfizzle.distillation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rfizzle.distillation.batch.BatchOwnerContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Threads the clicking player down to the brewing stand's ingredient-slot write ({@code
 * design/SPEC.md} §3 Ownership): while a brewing-stand menu click runs, {@link BatchOwnerContext}
 * holds the player, so the block entity's {@code setItem} records them as the batch owner. Every
 * other menu is left alone — the guard is a cheap {@code instanceof}.
 *
 * <p>Wrapped rather than HEAD/RETURN-bracketed so the context clears in a {@code finally}: a click
 * that throws (a bad slot id, another mod's mixin, a slot callback) must not leave a stale owner on
 * the server thread, or the next hopper insert would be misattributed to it.
 */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {

    @WrapMethod(method = "clicked")
    private void distillation$ownerContext(int slotId, int button, ClickType clickType, Player player,
                                           Operation<Void> original) {
        boolean brewing = (Object) this instanceof BrewingStandMenu;
        if (brewing) {
            BatchOwnerContext.set(player.getUUID());
        }
        try {
            original.call(slotId, button, clickType, player);
        } finally {
            if (brewing) {
                BatchOwnerContext.set(null);
            }
        }
    }
}
