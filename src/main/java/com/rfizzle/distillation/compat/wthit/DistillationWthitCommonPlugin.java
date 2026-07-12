package com.rfizzle.distillation.compat.wthit;

import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** WTHIT server-side registration: the stand's data provider (brew progress, rig, owner). */
public final class DistillationWthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(StandWthitProvider.INSTANCE, BrewingStandBlockEntity.class);
    }
}
