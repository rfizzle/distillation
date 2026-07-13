package com.rfizzle.distillation.arrow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Server-thread choke point over {@link PotionCauldronData}: charge a water cauldron with a potion,
 * read back the potion that tints it (validating the cauldron still holds water), and clear it. A
 * plain cauldron has no block entity to observe removal, so every read validates the block and
 * lazily drops a stale entry — the only invalidation the state gets.
 */
public final class PotionCauldrons {

    private PotionCauldrons() {
    }

    /** Records that this water cauldron is charged with {@code potion} (a keyless holder is a no-op). */
    public static void charge(ServerLevel level, BlockPos pos, Holder<Potion> potion) {
        potion.unwrapKey().ifPresent(key ->
                PotionCauldronData.getOrCreate(level).put(pos.asLong(), key.location()));
    }

    /**
     * The potion tinting this cauldron, if it is charged and still a water cauldron with a level to
     * dip. A charged position whose block is no longer a water cauldron is stale — cleared here and
     * reported empty.
     */
    public static Optional<Holder<Potion>> chargedPotion(ServerLevel level, BlockPos pos) {
        PotionCauldronData data = PotionCauldronData.getIfPresent(level);
        if (data == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> stored = data.get(pos.asLong());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!isChargeableWater(level.getBlockState(pos))) {
            data.remove(pos.asLong());
            return Optional.empty();
        }
        return resolvePotion(stored.get());
    }

    /** Clears any charge at this position. */
    public static void clear(ServerLevel level, BlockPos pos) {
        PotionCauldronData data = PotionCauldronData.getIfPresent(level);
        if (data != null) {
            data.remove(pos.asLong());
        }
    }

    /**
     * Visits every charged cauldron still backed by a water cauldron, dropping stale entries as it
     * goes — the particle sweep's source and the state's passive cleanup.
     */
    public static void forEachCharged(ServerLevel level, BiConsumer<BlockPos, Holder<Potion>> visitor) {
        PotionCauldronData data = PotionCauldronData.getIfPresent(level);
        if (data == null) {
            return;
        }
        for (var entry : data.entries()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!isChargeableWater(level.getBlockState(pos))) {
                data.remove(entry.getKey());
                continue;
            }
            resolvePotion(entry.getValue()).ifPresent(potion -> visitor.accept(pos, potion));
        }
    }

    /** A water cauldron with at least one level holds enough liquid to tint and to dip. */
    static boolean isChargeableWater(BlockState state) {
        return state.is(Blocks.WATER_CAULDRON)
                && state.hasProperty(LayeredCauldronBlock.LEVEL)
                && state.getValue(LayeredCauldronBlock.LEVEL) >= LayeredCauldronBlock.MIN_FILL_LEVEL;
    }

    private static Optional<Holder<Potion>> resolvePotion(ResourceLocation id) {
        return BuiltInRegistries.POTION.getOptional(id).map(BuiltInRegistries.POTION::wrapAsHolder);
    }
}
