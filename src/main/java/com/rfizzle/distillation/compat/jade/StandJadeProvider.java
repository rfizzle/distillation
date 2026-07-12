package com.rfizzle.distillation.compat.jade;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.compat.common.BrewingStandProbeTooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade adapter for the brewing-stand line — delegates only; the writer and formatter live in common. */
public enum StandJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            BrewingStandProbeTooltip.writeServerData(tag, level, accessor.getPosition(), accessor.getBlockEntity());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : BrewingStandProbeTooltip.buildLines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Distillation.id("brewing_stand");
    }
}
