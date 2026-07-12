package com.rfizzle.distillation.compat.wthit;

import com.rfizzle.distillation.compat.common.CauldronRigProbeTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

/**
 * WTHIT adapter for the cauldron "batch rig" line. Client-only body provider: the cauldron has no
 * block entity, and every input is a synced block state read straight from the world.
 */
public enum CauldronWthitProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        for (Component line : CauldronRigProbeTooltip.buildLines(accessor.getLevel(), accessor.getPosition())) {
            tooltip.addLine(line);
        }
    }
}
