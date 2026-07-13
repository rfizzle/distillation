package com.rfizzle.distillation.item;

import com.rfizzle.distillation.brew.HonestDurations;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Sip-half draughts ({@code design/SPEC.md} §4): sneaking on a full, drinkable, non-instant potion
 * drinks a half and leaves a marked half-draught; drinking the half applies the remainder and
 * returns the glass bottle. The classification is a pure decision ({@link #classify(boolean, boolean,
 * boolean, boolean)}) behind a thin shell that reads the stack, the drinker, and the effective
 * config; the drink itself is a faithful reimplementation of vanilla's {@code PotionItem
 * .finishUsingItem} with the effects halved.
 */
public final class Draughts {

    /** What a drink resolves to once the stack, the drinker's crouch, and the config are known. */
    public enum DrinkKind {
        /** Untouched vanilla drink — the mixin returns without intercepting. */
        FULL,
        /** A full potion sipped in half: apply ⌊duration ÷ 2⌋, leave a marked half draught. */
        SIP_HALF,
        /** A marked half draught: apply the remaining ⌊duration ÷ 2⌋, return the glass bottle. */
        DRINK_HALF
    }

    private Draughts() {
    }

    /** A potion already sipped down to its half. */
    public static boolean isDraught(ItemStack stack) {
        return stack.has(DistillationItems.DRAUGHT);
    }

    /**
     * The drink time for a resolved drink kind ({@code design/SPEC.md} §4): a draught is a
     * half-measure, and a half-measure swallows in half the time — a sip of a full bottle or a stored
     * half both take {@code ⌊vanillaTicks ÷ 2⌋}; a full drink keeps vanilla's time. Pure over the
     * classified kind and vanilla's own use duration, so the number tracks vanilla if it ever moves.
     */
    public static int useDuration(DrinkKind kind, int vanillaTicks) {
        return kind == DrinkKind.FULL ? vanillaTicks : vanillaTicks / 2;
    }

    /**
     * The pure sip/drink decision. A half draught always drinks its remaining half — even with
     * {@code enableDraughts} off, so existing halves stay drinkable. A full potion sips only when
     * sipping is enabled, the drinker is sneaking, and the potion has a non-instant effect (instant
     * potions and effectless water bottles fall through to a normal drink).
     */
    public static DrinkKind classify(boolean isDraught, boolean draughtsEnabled,
                                     boolean sneaking, boolean hasNonInstantEffect) {
        if (isDraught) {
            return DrinkKind.DRINK_HALF;
        }
        if (draughtsEnabled && sneaking && hasNonInstantEffect) {
            return DrinkKind.SIP_HALF;
        }
        return DrinkKind.FULL;
    }

    /**
     * The kind governing a drink already in progress: the value latched at {@code startUsingItem}
     * when the drinker is mid-drink on this exact stack ({@link DraughtDrinker}), else a live
     * classification. Latching keeps the shortened drink time (decided once at the start) and the
     * halved dose (decided at completion) in agreement — a released sneak mid-drink no longer flips
     * one without the other. A direct {@code finishUsingItem} call that never started a use (e.g. a
     * test, or a foreign consumer) has no latch and classifies live.
     */
    public static DrinkKind kindFor(ItemStack stack, LivingEntity entity) {
        if (entity instanceof DraughtDrinker drinker && entity.isUsingItem() && entity.getUseItem() == stack) {
            DrinkKind latched = drinker.distillation$drinkKind();
            if (latched != null) {
                return latched;
            }
        }
        return classify(stack, entity);
    }

    /** The shell classification for the drink seam: gathers the booleans and defers to the core. */
    public static DrinkKind classify(ItemStack stack, LivingEntity entity) {
        // A creative drinker consumes nothing, so a sip would apply a silent half-dose with no half to
        // keep — treat their sneak as a normal full drink instead (SPEC §4 is silent on creative).
        boolean sneaking = entity instanceof Player player
                && player.isShiftKeyDown()
                && !player.hasInfiniteMaterials();
        return classify(isDraught(stack), RecipeGraphs.effectiveConfig().enableDraughts,
                sneaking, hasNonInstantEffect(stack));
    }

    private static boolean hasNonInstantEffect(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (!instance.getEffect().value().isInstantenous()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finishes a sip or half drink — a faithful reimplementation of {@code PotionItem
     * .finishUsingItem} that halves each non-instant effect's (already honest-retuned) duration.
     * SIP_HALF leaves the drunk stack as a marked half in hand; DRINK_HALF consumes it and hands
     * back the glass bottle exactly as vanilla does.
     */
    public static ItemStack finishDraught(ItemStack stack, Level level, LivingEntity entity, DrinkKind kind) {
        Player player = entity instanceof Player p ? p : null;
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (!level.isClientSide) {
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            contents.forEachEffect(instance -> {
                if (instance.getEffect().value().isInstantenous()) {
                    instance.getEffect().value().applyInstantenousEffect(
                            player, player, entity, instance.getAmplifier(), 1.0);
                } else {
                    entity.addEffect(HonestDurations.withDuration(instance, HonestDurations.half(instance.getDuration())));
                }
            });
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return kind == DrinkKind.SIP_HALF
                ? sip(stack, entity)
                : drinkHalf(stack, player, entity);
    }

    /**
     * The full potion becomes a half draught in hand — no bottle, nothing consumed. Only ever a
     * non-creative drinker reaches here: {@link #classify(ItemStack, LivingEntity)} suppresses the
     * sip for an infinite-materials drinker, so there is no full stack to mark by mistake.
     */
    private static ItemStack sip(ItemStack stack, LivingEntity entity) {
        entity.gameEvent(GameEvent.DRINK);
        stack.set(DistillationItems.DRAUGHT, true);
        return stack;
    }

    /** The half is drunk: consume it and return the glass bottle, mirroring vanilla's bottle logic. */
    private static ItemStack drinkHalf(ItemStack stack, @Nullable Player player, LivingEntity entity) {
        if (player != null) {
            stack.consume(1, player);
        }
        ItemStack result = stack;
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) {
                result = new ItemStack(Items.GLASS_BOTTLE);
            } else if (player != null) {
                player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return result;
    }
}
