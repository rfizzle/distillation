package com.rfizzle.distillation.batch;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.recipe.BrewSeam;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * The rigged stand's server tick ({@code design/SPEC.md} §3): a faithful mirror of vanilla's
 * {@code BrewingStandBlockEntity.serverTick} — the same fuel load, brew countdown, and bottle-bit
 * block-state update — plus the batch branch. A pass starting on a rigged stand engages the batch
 * row when {@link BatchBrew#canEngage} holds, paying the batch fuel and one cauldron water level up
 * front; completion (always, for an engaged pass) rides {@link BrewSeam}'s choke point, which reads
 * the persisted batch flag to fill six bottles.
 *
 * <p>Only invoked for a stand the mixin has decided to take over (rigged, or finishing an in-flight
 * batch); every other stand runs untouched vanilla, so the vanilla-parity guarantee holds by
 * construction.
 */
public final class BatchBrewTick {

    private static final int BREW_CYCLE_TICKS = 400;

    private BatchBrewTick() {
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity beStand) {
        BatchStand stand = (BatchStand) beStand;
        NonNullList<ItemStack> items = stand.distillation$items();
        DistillationConfig config = Distillation.getConfig();
        RecipeGraph graph = RecipeGraphs.forLevel(level);

        // Fuel load — vanilla: a blaze powder tops the counter to 20 uses when it runs dry.
        ItemStack fuelStack = items.get(4);
        if (stand.distillation$fuel() <= 0 && fuelStack.is(Items.BLAZE_POWDER)) {
            stand.distillation$setFuel(BrewingStandBlockEntity.FUEL_USES);
            fuelStack.shrink(1);
            beStand.setChanged();
        }

        boolean bottomBrewable = BrewSeam.isBrewable(graph, items, config.enableMurkyDraughts);
        boolean batchAffordable = BatchBrew.canEngage(beStand, level, pos, items, graph, config);
        boolean canBrew = bottomBrewable || batchAffordable;
        ItemStack ingredientStack = items.get(BatchBrew.INGREDIENT_SLOT);

        if (stand.distillation$brewTime() > 0) {
            stand.distillation$setBrewTime(stand.distillation$brewTime() - 1);
            boolean done = stand.distillation$brewTime() == 0;
            boolean batchPass = BatchStates.get(beStand).brewing();
            Item ingredient = stand.distillation$ingredient();
            boolean ingredientChanged = ingredient == null || !ingredientStack.is(ingredient);
            if (done && (batchPass || canBrew)) {
                // A committed batch pass completes unconditionally; a normal pass only while brewable.
                BrewSeam.completeBrew(level, pos, items);
            } else if (ingredientChanged || (!batchPass && !canBrew)) {
                // A swapped ingredient aborts either pass — vanilla parity, and it stops a batch from
                // completing against an ingredient it never paid for. A committed batch otherwise runs
                // to completion even if the rig broke mid-cycle: the fuel and water were already spent.
                stand.distillation$setBrewTime(0);
                if (batchPass) {
                    BatchStates.setBrewing(beStand, false);
                }
            }
            beStand.setChanged(); // vanilla marks the stand changed every tick while brewing
        } else if (canBrew && stand.distillation$fuel() > 0) {
            boolean engageBatch = batchAffordable;
            int fuelCost = engageBatch ? config.batchFuelCost : 1;
            if (engageBatch) {
                BatchBrew.consumeWaterLevel(level, pos);
            }
            stand.distillation$setFuel(stand.distillation$fuel() - fuelCost);
            stand.distillation$setBrewTime(BREW_CYCLE_TICKS);
            stand.distillation$setIngredient(ingredientStack.getItem());
            BatchStates.setBrewing(beStand, engageBatch);
            beStand.setChanged();
        }

        updateBottleBits(level, pos, state, stand, items);
    }

    /** Vanilla's cosmetic block-state update: which of the three bottom slots shows a bottle. */
    private static void updateBottleBits(Level level, BlockPos pos, BlockState state, BatchStand stand,
                                         NonNullList<ItemStack> items) {
        boolean[] bits = new boolean[3];
        for (int i = 0; i < 3; i++) {
            bits[i] = !items.get(i).isEmpty();
        }
        if (Arrays.equals(bits, stand.distillation$lastPotionCount())) {
            return;
        }
        stand.distillation$setLastPotionCount(bits);
        if (!(state.getBlock() instanceof BrewingStandBlock)) {
            return;
        }
        BlockState updated = state;
        for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
            updated = updated.setValue(BrewingStandBlock.HAS_BOTTLE[i], bits[i]);
        }
        level.setBlock(pos, updated, 2);
    }
}
