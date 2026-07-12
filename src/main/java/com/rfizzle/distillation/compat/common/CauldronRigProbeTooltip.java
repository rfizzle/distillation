package com.rfizzle.distillation.compat.common;

import com.rfizzle.distillation.batch.BatchRig;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The viewer-agnostic core of the cauldron probe line ({@code design/SPEC.md} §Compatibility): the
 * "batch rig" line with its heat state, shown on a water cauldron sitting directly under a rigged
 * brewing stand. Every input — the cauldron level, the heat below, the stand above — is a synced
 * block state, so this reads the (client) world directly; no server-data round trip is needed. The
 * server-authoritative {@code enableBatchBrewing} toggle is read through the synced-first
 * {@link RecipeGraphs#effectiveConfig()}, not the client's local file, so a disabled feature shows
 * nothing even when the client's own config differs from the server's.
 */
public final class CauldronRigProbeTooltip {

    private CauldronRigProbeTooltip() {
    }

    public static List<Component> buildLines(BlockGetter level, BlockPos cauldronPos) {
        List<Component> lines = new ArrayList<>();
        if (!RecipeGraphs.effectiveConfig().enableBatchBrewing) {
            return lines;
        }
        // The rig is read from the stand's position; a cauldron participates only with a stand above it.
        BlockPos standPos = cauldronPos.above();
        if (!level.getBlockState(standPos).is(Blocks.BREWING_STAND)) {
            return lines;
        }
        BatchRig.Status rig = BatchRig.detect(level, standPos);
        if (!rig.rigged()) {
            return lines; // no heat, or the cauldron ran dry: not a live rig
        }
        lines.add(Component.translatable("tooltip.distillation.probe.cauldron_rig"));
        if (rig.heat() != null) {
            lines.add(Component.translatable("tooltip.distillation.probe.heat",
                    Component.translatable(rig.heat().translationKey())));
        }
        return lines;
    }
}
