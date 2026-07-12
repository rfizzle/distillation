package com.rfizzle.distillation.batch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * A stand's batch bookkeeping ({@code design/SPEC.md} §3), persisted on its block entity so both
 * survive a chunk unload: the {@code owner} — the UUID of the player who last inserted into the
 * ingredient slot, whose discoveries gate the batch row — and {@code brewing} — whether the pass
 * now running was engaged as a batch, so completion fills six bottles even if the rig was broken
 * mid-cycle (the pass was already paid for).
 *
 * <p>Immutable; the stand's attachment is swapped whole through {@link BatchStates}.
 */
public record BatchState(Optional<UUID> owner, boolean brewing) {

    public static final BatchState EMPTY = new BatchState(Optional.empty(), false);

    public static final Codec<BatchState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BatchState::owner),
            Codec.BOOL.optionalFieldOf("brewing", false).forGetter(BatchState::brewing)
    ).apply(instance, BatchState::new));

    /** True when nothing is worth persisting — the attachment is dropped so idle stands stay latent. */
    public boolean isEmpty() {
        return owner.isEmpty() && !brewing;
    }

    public BatchState withOwner(Optional<UUID> owner) {
        return new BatchState(owner, brewing);
    }

    public BatchState withBrewing(boolean brewing) {
        return new BatchState(owner, brewing);
    }
}
