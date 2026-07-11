package com.rfizzle.distillation.item;

import com.rfizzle.distillation.recipe.MurkyHints;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;
import java.util.Optional;

/**
 * The bottled failed brew ({@code design/SPEC.md} §1): its tooltip names the hint ingredient
 * ("Perhaps a … would have taken."), and drinking applies Nausea 0:15 plus the flicker — the
 * hinted conversion's output at amplifier 0, duration effects capped at 0:20, instants once. A
 * hintless draught is nausea alone. Drinking never records discovery (discovery only rides the
 * stand's output slot) and returns the glass bottle, mirroring vanilla's potion drink shape.
 */
public class MurkyDraughtItem extends Item {

    private static final int DRINK_DURATION_TICKS = 32;
    /** Nausea 0:15. */
    private static final int NAUSEA_TICKS = 300;
    /** The flicker's duration cap, 0:20 — one vanilla brew cycle. */
    private static final int FLICKER_CAP_TICKS = 400;

    public MurkyDraughtItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
            applyFlicker(stack, level, entity);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (player != null) {
                player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    /**
     * The taste of the brew the hint pointed at: the hinted conversion's output potion at
     * amplifier 0, duration effects capped at {@link #FLICKER_CAP_TICKS}, instant effects applied
     * once. Nothing applies for a hintless draught or a hint the live graph no longer resolves.
     */
    private static void applyFlicker(ItemStack stack, Level level, LivingEntity entity) {
        MurkyDraughtContents contents = stack.get(DistillationItems.MURKY_DRAUGHT_CONTENTS);
        if (contents == null || contents.hintIngredient().isEmpty()) {
            return;
        }
        Optional<Holder<Potion>> flicker = MurkyHints.flickerPotion(RecipeGraphs.forLevel(level),
                contents.inputPotion(), contents.hintIngredient().get());
        if (flicker.isEmpty()) {
            return;
        }
        for (MobEffectInstance effect : flicker.get().value().getEffects()) {
            if (effect.getEffect().value().isInstantenous()) {
                effect.getEffect().value().applyInstantenousEffect(entity, entity, entity, 0, 1.0);
            } else {
                entity.addEffect(new MobEffectInstance(effect.getEffect(),
                        Math.min(effect.getDuration(), FLICKER_CAP_TICKS), 0));
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(hintLine(stack.get(DistillationItems.MURKY_DRAUGHT_CONTENTS))
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * The hint line: the hinted ingredient by name, or the hintless wording — also used when the
     * component is missing entirely (a bare {@code /give}) or the hinted item is no longer
     * registered, both of which drink as nausea alone.
     */
    private static MutableComponent hintLine(MurkyDraughtContents contents) {
        Optional<ResourceLocation> hint = contents == null ? Optional.empty() : contents.hintIngredient();
        return hint.flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(item -> Component.translatable("tooltip.distillation.murky.hint", item.getDescription()))
                .orElseGet(() -> Component.translatable("tooltip.distillation.murky.hintless"));
    }
}
