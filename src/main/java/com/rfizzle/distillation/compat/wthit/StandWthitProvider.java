package com.rfizzle.distillation.compat.wthit;

import com.rfizzle.distillation.compat.common.BrewingStandProbeTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** WTHIT adapter for the brewing-stand line — delegates to the common writer and formatter. */
public enum StandWthitProvider implements IDataProvider<BrewingStandBlockEntity>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<BrewingStandBlockEntity> accessor, IPluginConfig config) {
        BrewingStandBlockEntity be = accessor.getTarget();
        BrewingStandProbeTooltip.writeServerData(data.raw(), accessor.getLevel(), be.getBlockPos(), be);
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        for (Component line : BrewingStandProbeTooltip.buildLines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
