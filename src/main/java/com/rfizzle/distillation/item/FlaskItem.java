package com.rfizzle.distillation.item;

import com.rfizzle.distillation.brew.HonestDurations;
import com.rfizzle.distillation.brew.TopUpDrinking;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * The multi-dose flask ({@code design/SPEC.md} §12): a copper-and-glass vessel holding up to three
 * doses of one discovered brew, drunk a dose at a time and refillable forever. The brew rides the
 * vanilla {@link DataComponents#POTION_CONTENTS} on the stack (so effects, color, and the §4 honest
 * durations all reuse vanilla's machinery; the item name stays "Flask" and the tooltip names the
 * contents) and the remaining half-doses ride {@link
 * DistillationItems#FLASK_DOSES}. Drinking composes with the sip-half draughts of §4 — a sneak-sip
 * takes half a dose and leaves a pending half — through the shared {@link Draughts.DrinkKind} state
 * machine and the same drink-time and top-up seams a potion draught uses. Filling is a pour ({@link
 * FlaskPour}, off-hand gesture) or a batch pass ({@code BrewSeam}, §3); the pure dose arithmetic and
 * classification live in {@link Flask}.
 */
public class FlaskItem extends Item {

    private static final int FULL_DRINK_TICKS = 32;

    public FlaskItem(Properties properties) {
        super(properties);
    }

    /** The flask's remaining half-doses (two per whole dose), or zero for an empty flask. */
    public static int doses(ItemStack stack) {
        return stack.getOrDefault(DistillationItems.FLASK_DOSES, 0);
    }

    /** The brew a flask holds, or {@link PotionContents#EMPTY} when it is empty. */
    public static PotionContents brew(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
    }

    /**
     * Writes the remaining half-doses back, clearing both the count and the brew when the flask
     * empties — so a drained flask is brew-less and refillable with anything again (SPEC §12).
     */
    public static void setDoses(ItemStack stack, int halves) {
        if (Flask.isEmpty(halves)) {
            stack.remove(DistillationItems.FLASK_DOSES);
            stack.remove(DataComponents.POTION_CONTENTS);
        } else {
            stack.set(DistillationItems.FLASK_DOSES, halves);
        }
    }

    /** Whether {@code brew} may enter this flask: room left, and the flask is empty or already holds it. */
    public static boolean canFill(ItemStack flask, PotionContents brew) {
        int halves = doses(flask);
        return !Flask.isFull(halves) && (Flask.isEmpty(halves) || brew.equals(brew(flask)));
    }

    /** Pours one dose in (SPEC §12): an empty flask adopts the brew, then a full dose is added. */
    public static void addDose(ItemStack flask, PotionContents brew) {
        if (Flask.isEmpty(doses(flask))) {
            flask.set(DataComponents.POTION_CONTENTS, brew);
        }
        setDoses(flask, Flask.addDoseHalves(doses(flask)));
    }

    /** Fills the flask to its three-dose capacity with {@code brew} — the batch pass's "in one go". */
    public static void fillToFull(ItemStack flask, PotionContents brew) {
        if (Flask.isEmpty(doses(flask))) {
            flask.set(DataComponents.POTION_CONTENTS, brew);
        }
        setDoses(flask, Flask.MAX_HALVES);
    }

    /**
     * The drink kind governing a flask drink in progress: the value latched at {@code startUsingItem}
     * ({@link DraughtDrinker}, shared with potion draughts), else a live classification. Latching keeps
     * the shortened sip time and the halved dose in agreement across a mid-drink sneak toggle, exactly
     * as {@link Draughts#kindFor} does for potions.
     */
    public static Draughts.DrinkKind kindFor(ItemStack stack, LivingEntity entity) {
        if (entity instanceof DraughtDrinker drinker && entity.isUsingItem() && entity.getUseItem() == stack) {
            Draughts.DrinkKind latched = drinker.distillation$drinkKind();
            if (latched != null) {
                return latched;
            }
        }
        return classifyKind(stack, entity);
    }

    /** The live sip/full/half decision for a flask: gathers the drinker's crouch and the brew, defers to the core. */
    public static Draughts.DrinkKind classifyKind(ItemStack stack, LivingEntity entity) {
        // A creative drinker spends no dose (like a potion sip, SPEC §4), so a sneak would apply a
        // silent half with no half kept — treat their sneak as a normal full drink instead.
        boolean sneaking = entity instanceof Player player
                && player.isShiftKeyDown()
                && !player.hasInfiniteMaterials();
        return Flask.classify(doses(stack), sneaking, hasNonInstantEffect(brew(stack)));
    }

    private static boolean hasNonInstantEffect(PotionContents contents) {
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (!instance.getEffect().value().isInstantenous()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Flask.isEmpty(doses(stack))) {
            return InteractionResultHolder.pass(stack); // an empty flask has nothing to drink
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        Draughts.DrinkKind kind = kindFor(stack, entity);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (!level.isClientSide && !Flask.isEmpty(doses(stack))) {
            applyDose(stack, entity, player, kind);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        // The flask is reusable — a dose is spent (never in creative) but the vessel always stays.
        if (player == null || !player.hasInfiniteMaterials()) {
            setDoses(stack, Flask.halvesAfter(doses(stack), kind));
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    /**
     * Applies one dose (or half a dose), a faithful echo of {@code Draughts.finishDraught}: instant
     * effects apply once, non-instant effects apply their §4-honest duration — full, or ⌊÷2⌋ for a sip
     * or a stored half — routed through §4 top-up drinking against the same 2×-base cap as a bottle.
     */
    private static void applyDose(ItemStack stack, LivingEntity entity, Player player, Draughts.DrinkKind kind) {
        boolean topUp = RecipeGraphs.effectiveConfig().enableTopUpDrinking;
        boolean full = kind == Draughts.DrinkKind.FULL;
        brew(stack).forEachEffect(instance -> {
            if (instance.getEffect().value().isInstantenous()) {
                instance.getEffect().value().applyInstantenousEffect(
                        player, player, entity, instance.getAmplifier(), 1.0);
            } else {
                int base = instance.getDuration();
                MobEffectInstance dose = full
                        ? instance
                        : HonestDurations.withDuration(instance, HonestDurations.half(base));
                entity.addEffect(topUp ? TopUpDrinking.resolveInstance(entity, dose, base) : dose);
            }
        });
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Draughts.useDuration(kindFor(stack, entity), FULL_DRINK_TICKS);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getDrinkingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEatingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int halves = doses(stack);
        if (Flask.isEmpty(halves)) {
            tooltip.add(Component.translatable("tooltip.distillation.flask.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        // The brew's own effect lines (vanilla's format), then the doses-remaining line.
        brew(stack).addPotionTooltip(tooltip::add, 1.0F, context.tickRate());
        tooltip.add(Component.translatable("tooltip.distillation.flask.doses", dosesLabel(halves))
                .withStyle(ChatFormatting.GRAY));
    }

    /** The doses-remaining label: whole doses with a trailing "½" for a pending sipped half. */
    private static String dosesLabel(int halves) {
        int whole = Flask.wholeDoses(halves);
        if (Flask.hasPendingHalf(halves)) {
            return whole == 0 ? "½" : whole + "½";
        }
        return Integer.toString(whole);
    }
}
