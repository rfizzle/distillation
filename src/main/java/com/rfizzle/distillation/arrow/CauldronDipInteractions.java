package com.rfizzle.distillation.arrow;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The two cauldron dip interactions ({@code design/SPEC.md} §Tipped arrows), registered into the
 * vanilla {@code CauldronInteraction.WATER} map at init — no mixin, the map is public and mutable.
 * A drinkable, discovered potion charges a water cauldron; arrows dipped into a charged cauldron
 * tip, spending one water level per dip. Splash and lingering potions get no handler, so vanilla's
 * lingering-potion tipped-arrow recipe is untouched.
 *
 * <p>With {@code enableTippedArrows} off both handlers fall straight through to vanilla — the
 * potion handler delegates to the water-fill behavior it replaced, the arrow handler passes — so a
 * disabled feature is byte-identical to vanilla (the spec's parity contract).
 */
public final class CauldronDipInteractions {

    // Captured once at class-load, before register() installs ours: the water-bottle fill behavior
    // we delegate to for water potions and whenever the feature is off.
    private static final CauldronInteraction VANILLA_POTION = CauldronInteraction.WATER.map()
            .getOrDefault(Items.POTION,
                    (state, level, pos, player, hand, stack) -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);

    private CauldronDipInteractions() {
    }

    /** Installs the charge (potion) and dip (arrow) handlers on the water cauldron. */
    public static void register() {
        CauldronInteraction.WATER.map().put(Items.POTION, CauldronDipInteractions::onPotion);
        CauldronInteraction.WATER.map().put(Items.ARROW, CauldronDipInteractions::onArrow);
    }

    private static ItemInteractionResult onPotion(BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, ItemStack stack) {
        DistillationConfig config = Distillation.getConfig();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        // Feature off, a water bottle, or a potionless holder: vanilla's water-fill behavior, unchanged.
        if (!config.enableTippedArrows || contents == null || contents.is(Potions.WATER)) {
            return VANILLA_POTION.interact(state, level, pos, player, hand, stack);
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide); // client swings; server does the work
        }
        Holder<Potion> potion = contents.potion().orElse(null);
        if (potion == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        RecipeGraph graph = RecipeGraphs.forLevel(serverLevel);
        List<RecipeGraph.PotionConversion> producing = graph.conversionsProducing(potion);
        boolean discoveredAny = producing.stream()
                .anyMatch(conversion -> DiscoveryManager.data(serverPlayer).contains(conversion.id()));
        if (!ArrowTipping.chargeAllowed(config.enableDiscovery, discoveredAny, producing.size())) {
            // A tippable-in-principle potion the player hasn't learned: name the gate, don't just no-op.
            if (!producing.isEmpty()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.distillation.arrow_undiscovered"), true);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        PotionCauldrons.charge(serverLevel, pos, potion);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
        }
        serverPlayer.awardStat(Stats.USE_CAULDRON);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult onArrow(BlockState state, Level level, BlockPos pos, Player player,
                                                 InteractionHand hand, ItemStack stack) {
        DistillationConfig config = Distillation.getConfig();
        if (!config.enableTippedArrows) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        Holder<Potion> potion = PotionCauldrons.chargedPotion(serverLevel, pos).orElse(null);
        if (potion == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // uncharged cauldron: vanilla no-op
        }
        int count = ArrowTipping.arrowsPerDip(stack.getCount(), config.tippedArrowsPerDip);
        if (count <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack tipped = PotionContents.createItemStack(Items.TIPPED_ARROW, potion);
        tipped.setCount(count);
        if (!player.getAbilities().instabuild) {
            stack.shrink(count);
        }
        if (!serverPlayer.getInventory().add(tipped)) {
            serverPlayer.drop(tipped, false);
        }
        serverPlayer.awardStat(Stats.USE_CAULDRON);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
        // One water level per dip; when the last level drains the cauldron empties and the charge is stale.
        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        if (!PotionCauldrons.isChargeableWater(level.getBlockState(pos))) {
            PotionCauldrons.clear(serverLevel, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
