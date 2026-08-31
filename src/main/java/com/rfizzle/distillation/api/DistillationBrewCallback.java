package com.rfizzle.distillation.api;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side from Distillation's single brew-completion choke point after a brew cycle
 * finishes — observation only. It runs once per completed cycle at a heated stand, for both a
 * normal three-bottle pass and a six-bottle batch pass ({@code design/SPEC.md} §3), whether the
 * cycle produced real potions, Murky Draughts ({@code design/SPEC.md} §1), or a mix.
 *
 * <p>The results list is an immutable snapshot of the bottles the cycle produced; potion identity
 * is the recipe graph's job, not a mutation surface, so listeners must not retain or mutate the
 * stacks. A listener that throws is caught, logged, and skipped — a misbehaving observer can never
 * break brewing.
 */
@Stable
public interface DistillationBrewCallback {

    /** One-shot gate so a listener that throws every cycle logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<DistillationBrewCallback> EVENT = EventFactory.createArrayBacked(DistillationBrewCallback.class,
            listeners -> (level, pos, ingredient, results, batchOwner, batch) -> {
                for (DistillationBrewCallback listener : listeners) {
                    try {
                        listener.onBrew(level, pos, ingredient, results, batchOwner, batch);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Distillation.LOGGER.warn("A DistillationBrewCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * @param level       the server level the stand sits in
     * @param pos         the brewing stand's position
     * @param ingredient  a copy of the ingredient stack consumed this cycle (pre-shrink)
     * @param results     an immutable snapshot of the bottles the cycle produced (never mutate)
     * @param batchOwner  the batch owner's UUID, or {@code null} for a normal (non-batch) pass
     * @param batch       {@code true} when this was a six-bottle batch pass ({@code design/SPEC.md} §3)
     */
    void onBrew(ServerLevel level, BlockPos pos, ItemStack ingredient, List<ItemStack> results,
                @Nullable UUID batchOwner, boolean batch);
}
