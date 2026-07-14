package com.rfizzle.distillation.item;

import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The pour gesture ({@code design/SPEC.md} §12): right-click-using a bottled potion in the main hand
 * with a compatible flask in the off-hand pours one dose into the flask and returns the glass bottle,
 * instead of drinking. Full player context, so the discovery gate and the murky/splash/water/draught
 * exclusions are enforced server-side (splash and lingering never reach here — they override
 * {@code use} to throw). The shell over {@link FlaskFill}'s pure gate; {@link
 * com.rfizzle.distillation.mixin.PotionItemPourMixin} is the seam that calls it.
 */
public final class FlaskPour {

    private FlaskPour() {
    }

    /**
     * Attempts to pour the main-hand potion into the off-hand flask. Returns the interaction result
     * when the gesture is a pour (a dose added, or a learned-recipe gate that stops the drink), or
     * {@code null} to fall through to a normal drink — a full or brew-mismatched flask, a water bottle,
     * or a half draught all just drink as usual.
     */
    @Nullable
    public static InteractionResultHolder<ItemStack> tryPour(Level level, Player player, InteractionHand hand,
                                                             ItemStack potion, ItemStack flask) {
        DistillationConfig config = RecipeGraphs.effectiveConfig();
        if (!config.enableFlask) {
            return null;
        }
        PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
        // Only a real, drinkable brew pours: a water bottle or a half draught is not a full dose.
        if (contents == null || contents.is(Potions.WATER) || Draughts.isDraught(potion)) {
            return null;
        }
        if (!FlaskItem.canFill(flask, contents)) {
            return null; // full, or a different brew than the flask holds — let them drink instead
        }
        // From here the off-hand flask makes this a pour attempt; the rest is server-authoritative.
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(potion, level.isClientSide); // client swings; server works
        }
        Holder<Potion> potionHolder = contents.potion().orElse(null);
        int producing = 0;
        boolean discoveredAny = false;
        if (potionHolder != null) {
            List<RecipeGraph.PotionConversion> producers =
                    RecipeGraphs.forLevel(serverLevel).conversionsProducing(potionHolder);
            producing = producers.size();
            discoveredAny = producers.stream()
                    .anyMatch(conversion -> DiscoveryManager.data(serverPlayer).contains(conversion.id()));
        }
        if (!FlaskFill.discoveredProducer(config.enableDiscovery, discoveredAny, producing)) {
            // A brew the player could pour but has not learned: name the gate, and don't drink it away.
            serverPlayer.displayClientMessage(
                    Component.translatable("message.distillation.flask_undiscovered", potion.getHoverName()), true);
            return InteractionResultHolder.consume(potion);
        }
        FlaskItem.addDose(flask, contents);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(potion, player, new ItemStack(Items.GLASS_BOTTLE)));
        }
        serverPlayer.awardStat(Stats.ITEM_USED.get(potion.getItem()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
