package com.rfizzle.distillation.item;

import org.jetbrains.annotations.Nullable;

/**
 * A {@link net.minecraft.world.entity.LivingEntity} that has latched its draught decision for the
 * drink in progress ({@code design/SPEC.md} §4). The sip/full choice is fixed when the drink starts
 * — from the crouch and stack at that instant — so releasing the sneak mid-drink cannot turn a sip
 * into a full drink (or the reverse), and the shortened drink time and the halved dose always agree.
 * The brewing-stand duck interfaces ({@code batch/BatchStand}) follow the same mixin-exposes-a-field
 * pattern.
 */
public interface DraughtDrinker {

    /** The kind latched at the start of the current drink, or {@code null} when no potion is in use. */
    @Nullable
    Draughts.DrinkKind distillation$drinkKind();
}
