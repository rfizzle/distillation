package com.rfizzle.distillation.arrow;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * A budgeted server sweep ({@code mc-tick-work}) that lifts potion-colored particles off each
 * charged cauldron, so a charged cauldron reads as such in-world without any client sync. Runs on
 * an interval, only touches the per-dimension {@link PotionCauldronData} when one already exists
 * (never creating it), and doubles as the passive cleanup for stale entries via
 * {@link PotionCauldrons#forEachCharged}. Charged cauldrons are few, so the per-tick cost is bounded
 * by construction.
 */
public final class ChargedCauldronParticles {

    private static final int INTERVAL_TICKS = 10;
    private static final int PARTICLES_PER_CAULDRON = 2;
    /** Water sits a little below the cauldron rim; lift particles from that surface. */
    private static final double SURFACE_HEIGHT = 0.9;
    private static final double SPREAD = 0.18;

    private ChargedCauldronParticles() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(ChargedCauldronParticles::tick);
    }

    private static void tick(ServerLevel level) {
        if (!Distillation.getConfig().enableTippedArrows) {
            return;
        }
        if (level.getGameTime() % INTERVAL_TICKS != 0) {
            return;
        }
        if (PotionCauldronData.getIfPresent(level) == null) {
            return; // nothing charged in this dimension — never create the store just to read it
        }
        // Guard the sweep: one bad entry must log and skip, never escape into the shared tick event.
        try {
            PotionCauldrons.forEachCharged(level, (pos, potion) -> {
                ColorParticleOption particle =
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, PotionContents.getColor(potion));
                level.sendParticles(particle,
                        pos.getX() + 0.5, pos.getY() + SURFACE_HEIGHT, pos.getZ() + 0.5,
                        PARTICLES_PER_CAULDRON, SPREAD, 0.0, SPREAD, 0.0);
            });
        } catch (Exception e) {
            Distillation.LOGGER.error("Failed to sweep charged cauldrons in {}", level.dimension().location(), e);
        }
    }
}
