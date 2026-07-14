package com.rfizzle.distillation.mixin;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchBrew;
import com.rfizzle.distillation.batch.BatchBrewTick;
import com.rfizzle.distillation.batch.BatchRig;
import com.rfizzle.distillation.batch.BatchStand;
import com.rfizzle.distillation.batch.BatchStates;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.Draughts;
import com.rfizzle.distillation.recipe.BrewSeam;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes the brewing stand through the recipe graph ({@code design/SPEC.md} §1) and grows it into a
 * batch stand (§3). Brew completion resolves per bottle in {@link BrewSeam} — the one choke point —
 * and the cycle-start and ingredient-slot gates read the graph, so conversions removed by config
 * neither brew nor start cycles.
 *
 * <p>The container is widened to eight slots (5–7 = the batch row); {@code WorldlyContainer} face
 * arrays already omit those indices, so hoppers can neither see nor touch the batch slots. A rigged
 * stand's server tick is taken over by {@link BatchBrewTick}; every other stand runs untouched
 * vanilla, so the vanilla-parity guarantee holds by construction.
 */
@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin implements BatchStand {

    @Shadow
    private NonNullList<ItemStack> items;

    @Shadow
    int brewTime;

    @Shadow
    int fuel;

    @Shadow
    private Item ingredient;

    @Shadow
    private boolean[] lastPotionCount;

    private boolean distillation$rigged;

    // Grow the container to eight slots the moment the block entity is built, before any load. The
    // batch row (5–7) rides vanilla's own NBT save/load, which sizes to getContainerSize().
    @Inject(method = "<init>", at = @At("TAIL"))
    private void distillation$growContainer(BlockPos pos, BlockState state, CallbackInfo ci) {
        this.items = NonNullList.withSize(BatchBrew.CONTAINER_SIZE, ItemStack.EMPTY);
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void distillation$batchTick(Level level, BlockPos pos, BlockState state,
                                               BrewingStandBlockEntity stand, CallbackInfo ci) {
        BatchStand batch = (BatchStand) stand;
        boolean wasRigged = batch.distillation$isRigged();
        boolean batchEnabled = Distillation.getConfig().enableBatchBrewing;
        BatchRig.Status rig = batchEnabled ? BatchRig.detect(level, pos) : null;
        boolean rigged = rig != null && rig.rigged();
        batch.distillation$setRigged(rigged);
        // A rig forming or dropping while the batch row holds bottles flips whether the brew-state
        // comparator (SPEC §9) counts slots 5–7, but an idle stand fires no per-tick setChanged, so
        // nudge neighbours to repaint on the transition. Gated on the feature because vanilla's
        // fullness signal counts the hidden row either way and needs no nudge — feature-off stays
        // behaviorally vanilla. (Set the rigged flag first, above, so the fresh state is what a
        // repainting comparator reads back.)
        if (wasRigged != rigged && Distillation.getConfig().enableComparatorOutput
                && distillation$batchRowOccupied(batch.distillation$items())) {
            stand.setChanged();
        }
        // Take over for a rigged stand, or one still finishing a committed batch pass; hand every
        // other stand back to untouched vanilla.
        boolean brewing = BatchStates.get(stand).brewing();
        if (!rigged && !brewing) {
            // Eject a stranded batch row only when the rig was physically dismantled (cauldron or
            // heat removed) while batching is on — never when the cauldron merely ran dry (the
            // player refills it; the row hides until then), and never when the feature is off, which
            // must stay behaviorally inert (the row is left alone, not ejected).
            boolean dismantled = rig != null
                    && (rig.piece() == BatchRig.Piece.NO_CAULDRON || rig.piece() == BatchRig.Piece.NO_HEAT);
            if (dismantled && BatchBrew.ejectBatchRow(level, pos, batch.distillation$items())) {
                stand.setChanged();
            }
            return;
        }
        ci.cancel();
        BatchBrewTick.serverTick(level, pos, state, stand);
    }

    private static boolean distillation$batchRowOccupied(NonNullList<ItemStack> items) {
        for (int slot = BatchBrew.FIRST_BATCH_SLOT; slot <= BatchBrew.LAST_BATCH_SLOT; slot++) {
            if (!items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // The one brew-completion choke point for the vanilla (non-rigged) path: replace doBrew with the
    // seam so conversions removed by config stop brewing and invalid pairs murk (SPEC §1). A rigged
    // stand instead routes through BatchBrewTick, which calls the same seam.
    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void distillation$brewThroughSeam(Level level, BlockPos pos, NonNullList<ItemStack> items,
                                                     CallbackInfo ci) {
        ci.cancel();
        BrewSeam.completeBrew(level, pos, items);
    }

    @Inject(method = "isBrewable", at = @At("HEAD"), cancellable = true)
    private static void distillation$gateCycleStart(PotionBrewing brewing, NonNullList<ItemStack> items,
                                                    CallbackInfoReturnable<Boolean> cir) {
        // Only vanilla's serverTick calls this, so the local (server) config is authoritative.
        var config = Distillation.getConfig();
        cir.setReturnValue(BrewSeam.isBrewable(
                RecipeGraphs.lookup(brewing, config.enableMissingBrews, config.enablePremiumBrews,
                        config.enableAntidotes),
                items, config.enableMurkyDraughts));
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void distillation$gateIngredientSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Draughts.isDraught(stack)) {
            // A half draught is not a receptive bottle (SPEC §4) — no topping up, in any slot,
            // including the batch row (5–7) which vanilla's own canPlaceItem would otherwise accept.
            cir.setReturnValue(false);
            return;
        }
        if (slot >= BatchBrew.FIRST_BATCH_SLOT && slot <= BatchBrew.LAST_BATCH_SLOT
                && stack.is(DistillationItems.FLASK)) {
            // A flask is a valid batch-row occupant (§12) that vanilla's own canPlaceItem rejects;
            // accept it only while the feature is on, so an off flask stays a plain drink item. The
            // bottom row and ingredient/fuel slots never take a flask.
            cir.setReturnValue(RecipeGraphs.effectiveConfig().enableFlask);
            return;
        }
        if (slot != 3) {
            return; // bottle, fuel, and batch slots stay vanilla (vanilla accepts bottles in 5–7 already)
        }
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null) {
            return; // detached block entity: vanilla's PotionBrewing.EMPTY fallback rejects everything
        }
        cir.setReturnValue(RecipeGraphs.forLevel(level).isIngredient(stack));
    }

    // ---- BatchStand ----

    @Override
    public boolean distillation$isRigged() {
        return this.distillation$rigged;
    }

    @Override
    public void distillation$setRigged(boolean rigged) {
        this.distillation$rigged = rigged;
    }

    @Override
    public NonNullList<ItemStack> distillation$items() {
        return this.items;
    }

    @Override
    public int distillation$fuel() {
        return this.fuel;
    }

    @Override
    public void distillation$setFuel(int fuel) {
        this.fuel = fuel;
    }

    @Override
    public int distillation$brewTime() {
        return this.brewTime;
    }

    @Override
    public void distillation$setBrewTime(int brewTime) {
        this.brewTime = brewTime;
    }

    @Override
    public Item distillation$ingredient() {
        return this.ingredient;
    }

    @Override
    public void distillation$setIngredient(Item ingredient) {
        this.ingredient = ingredient;
    }

    @Override
    public boolean[] distillation$lastPotionCount() {
        return this.lastPotionCount;
    }

    @Override
    public void distillation$setLastPotionCount(boolean[] lastPotionCount) {
        this.lastPotionCount = lastPotionCount;
    }
}
