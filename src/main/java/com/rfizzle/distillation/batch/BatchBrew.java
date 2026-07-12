package com.rfizzle.distillation.batch;

import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Batch-pass engagement and its resource gates ({@code design/SPEC.md} §3): whether a rigged stand
 * scales a starting pass to six bottles, whose discoveries gate the batch row, and the cauldron
 * water a pass consumes. The per-bottle resolution itself rides {@link
 * com.rfizzle.distillation.recipe.BrewSeam}'s choke point.
 */
public final class BatchBrew {

    /** Container layout of the grown stand: bottom bottles 0–2, ingredient 3, fuel 4, batch row 5–7. */
    public static final int INGREDIENT_SLOT = 3;
    public static final int FIRST_BATCH_SLOT = 5;
    public static final int LAST_BATCH_SLOT = 7;
    public static final int CONTAINER_SIZE = 8;

    /**
     * Screen geometry of the batch row, shared by the menu (slot placement) and the screen (slot
     * backgrounds + steam): three slots in a strip hugging the top of the vanilla window, aligned
     * with the bottle-arc columns. {@code Y} is negative — the row sits just above the window top.
     */
    public static final int[] BATCH_SLOT_X = {56, 79, 102};
    public static final int BATCH_SLOT_Y = -20;

    private BatchBrew() {
    }

    /**
     * Whether a pass now starting engages the batch row: the rig is valid <em>at pass start</em>,
     * the ingredient slot holds at least {@link DistillationConfig#batchIngredientCost}, fuel covers
     * {@link DistillationConfig#batchFuelCost}, and at least one batch-row bottle will actually fill
     * (receptive, a conversion the ingredient takes, and one the owner may brew).
     */
    public static boolean canEngage(BrewingStandBlockEntity stand, Level level, BlockPos pos,
                                    NonNullList<ItemStack> items, RecipeGraph graph, DistillationConfig config) {
        if (!config.enableBatchBrewing || !BatchRig.isRigged(level, pos)) {
            return false;
        }
        int fillable = fillableBatchBottles(stand, level, items, graph, config, false);
        return engages(items.get(INGREDIENT_SLOT).getCount(), ((BatchStand) stand).distillation$fuel(),
                config.batchIngredientCost, config.batchFuelCost, fillable);
    }

    /**
     * The engagement arithmetic, isolated from the world for testing: a rigged pass scales to a
     * batch when the ingredient count covers the batch cost, fuel covers the batch fuel cost, and at
     * least one batch bottle would fill.
     */
    public static boolean engages(int ingredientCount, int fuel, int batchIngredientCost, int batchFuelCost,
                                  int fillableBatchBottles) {
        return ingredientCount >= batchIngredientCost && fuel >= batchFuelCost && fillableBatchBottles >= 1;
    }

    /**
     * How many batch-row bottles a pass would fill — a receptive bottle whose conversion the
     * ingredient takes and the owner may brew. {@code lenient} decides an offline owner: a pass at
     * start needs the owner online to verify a discovery (strict); a pass completing after the owner
     * logged off honors the commitment it already paid for (lenient).
     */
    public static int fillableBatchBottles(BrewingStandBlockEntity stand, Level level,
                                           NonNullList<ItemStack> items, RecipeGraph graph,
                                           DistillationConfig config, boolean lenient) {
        ItemStack ingredient = items.get(INGREDIENT_SLOT);
        int count = 0;
        for (int slot = FIRST_BATCH_SLOT; slot <= LAST_BATCH_SLOT; slot++) {
            if (batchConversion(stand, level, ingredient, items.get(slot), graph, config, lenient) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The conversion a batch-row bottle resolves to, or {@code null} to leave it untouched: the
     * ingredient must take the bottle to a graph conversion, and the owner must be allowed to brew
     * it. Batch bottles are never murked — an invalid or undiscovered one is simply skipped.
     */
    @Nullable
    public static RecipeGraph.Conversion batchConversion(BrewingStandBlockEntity stand, Level level,
                                                         ItemStack ingredient, ItemStack bottle,
                                                         RecipeGraph graph, DistillationConfig config,
                                                         boolean lenient) {
        if (bottle.isEmpty()) {
            return null;
        }
        RecipeGraph.Conversion conversion = graph.matchConversion(ingredient, bottle);
        if (conversion == null) {
            return null;
        }
        return ownerMayBrew(stand, level, conversion.id(), config, lenient) ? conversion : null;
    }

    /**
     * Whether the stand's batch owner may brew a conversion. Discovery disabled (or an owner who has
     * discovered it) allows it; an owner who has not, or a hopper-disowned stand, does not. An
     * offline owner allows only when {@code lenient} — a pass already committed completes.
     */
    private static boolean ownerMayBrew(BrewingStandBlockEntity stand, Level level, ResourceLocation conversionId,
                                        DistillationConfig config, boolean lenient) {
        if (!config.enableDiscovery) {
            return true; // every conversion counts as discovered; the rig gates on infrastructure and cost alone
        }
        Optional<UUID> owner = BatchStates.owner(stand);
        if (owner.isEmpty()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner.get());
        if (player == null) {
            return lenient;
        }
        return DiscoveryManager.data(player).contains(conversionId);
    }

    /** Consumes one cauldron water level at pass start; the rig only ever lowers water, never the cauldron. */
    public static void consumeWaterLevel(Level level, BlockPos standPos) {
        BlockPos cauldronPos = standPos.below();
        BlockState cauldron = level.getBlockState(cauldronPos);
        if (cauldron.hasProperty(LayeredCauldronBlock.LEVEL)) {
            LayeredCauldronBlock.lowerFillLevel(cauldron, level, cauldronPos);
        }
    }

    /**
     * Ejects any non-empty batch-row bottles above the stand and clears the slots (rig removed or
     * batching disabled). Returns whether anything was ejected, so the caller dirties the stand only
     * when the container actually changed.
     */
    public static boolean ejectBatchRow(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        boolean ejected = false;
        for (int slot = FIRST_BATCH_SLOT; slot <= LAST_BATCH_SLOT; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
                items.set(slot, ItemStack.EMPTY);
                ejected = true;
            }
        }
        return ejected;
    }
}
