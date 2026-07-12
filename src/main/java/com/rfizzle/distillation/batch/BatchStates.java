package com.rfizzle.distillation.batch;

import com.rfizzle.distillation.discovery.DistillationAttachments;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * The write choke point for a stand's {@link BatchState} attachment ({@code design/SPEC.md} §3):
 * every mutation lands here, marks the block entity changed (in-place attachment writes do not
 * auto-dirty a block entity), and removes the attachment outright when empty so idle stands stay
 * latent.
 */
public final class BatchStates {

    private BatchStates() {
    }

    /** The current state, or {@link BatchState#EMPTY} for a stand that has never batched. */
    public static BatchState get(BrewingStandBlockEntity stand) {
        BatchState state = stand.getAttached(DistillationAttachments.BATCH_STATE);
        return state == null ? BatchState.EMPTY : state;
    }

    /** The batch owner, if any — the player whose discoveries gate this stand's batch row. */
    public static Optional<UUID> owner(BrewingStandBlockEntity stand) {
        return get(stand).owner();
    }

    /** Records the player who just inserted into the ingredient slot as the batch owner. */
    public static void setOwner(BrewingStandBlockEntity stand, UUID owner) {
        BatchState state = get(stand);
        if (!state.owner().equals(Optional.of(owner))) {
            set(stand, state.withOwner(Optional.of(owner)));
        }
    }

    /** Clears the batch owner — a hopper insert into the ingredient slot disowns the stand. */
    public static void clearOwner(BrewingStandBlockEntity stand) {
        BatchState state = get(stand);
        if (state.owner().isPresent()) {
            set(stand, state.withOwner(Optional.empty()));
        }
    }

    /** Marks whether the pass now running was engaged as a batch. */
    public static void setBrewing(BrewingStandBlockEntity stand, boolean brewing) {
        BatchState state = get(stand);
        if (state.brewing() != brewing) {
            set(stand, state.withBrewing(brewing));
        }
    }

    private static void set(BrewingStandBlockEntity stand, BatchState state) {
        if (state.isEmpty()) {
            stand.removeAttached(DistillationAttachments.BATCH_STATE);
        } else {
            stand.setAttached(DistillationAttachments.BATCH_STATE, state);
        }
        stand.setChanged();
    }
}
