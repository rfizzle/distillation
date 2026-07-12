package com.rfizzle.distillation.compat.wthit;

import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BrewingStandBlock;

/** WTHIT client-side registration: the stand body line and the client-only cauldron rig line. */
public final class DistillationWthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(StandWthitProvider.INSTANCE, BrewingStandBlock.class);
        registrar.body(CauldronWthitProvider.INSTANCE, AbstractCauldronBlock.class);
    }
}
