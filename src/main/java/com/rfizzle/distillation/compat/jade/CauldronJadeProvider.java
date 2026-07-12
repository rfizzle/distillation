package com.rfizzle.distillation.compat.jade;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.common.CauldronRigProbeTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade adapter for the cauldron "batch rig" line. Client-only: the cauldron has no block entity and
 * every input is a synced block state, so it reads the world directly with no server-data round trip.
 */
public enum CauldronJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : CauldronRigProbeTooltip.buildLines(accessor.getLevel(), accessor.getPosition())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Distillation.id("cauldron_rig");
    }
}
