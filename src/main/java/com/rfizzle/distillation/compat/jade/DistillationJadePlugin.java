package com.rfizzle.distillation.compat.jade;

import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin ({@code design/SPEC.md} §Compatibility): the brewing-stand line (brew progress,
 * batch-rig status, batch owner) and the cauldron "batch rig" line. The stand's data provider keys on
 * the block-entity class (it needs the stand's server-side brew/owner state); the cauldron is a
 * client-only component keyed on the cauldron block, since its inputs are all synced block states.
 */
@WailaPlugin
public class DistillationJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(StandJadeProvider.INSTANCE, BrewingStandBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(StandJadeProvider.INSTANCE, BrewingStandBlock.class);
        registration.registerBlockComponent(CauldronJadeProvider.INSTANCE, AbstractCauldronBlock.class);
    }
}
