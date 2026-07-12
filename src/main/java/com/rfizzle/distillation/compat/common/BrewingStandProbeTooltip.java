package com.rfizzle.distillation.compat.common;

import com.mojang.authlib.GameProfile;
import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchRig;
import com.rfizzle.distillation.batch.BatchStand;
import com.rfizzle.distillation.batch.BatchStates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The viewer-agnostic core of the brewing-stand probe line ({@code design/SPEC.md} §Compatibility):
 * a server-side writer that packs brew progress, batch-rig status, and the batch owner into the
 * probe's tag, and a pure formatter that turns that tag back into localized lines. No Jade or WTHIT
 * imports live here, so the two adapters delegate to one place and cannot drift apart.
 *
 * <p>All three fields are server-only or load-stale on the client (the stand's {@code brewTime} is not
 * synced, the owner is a server attachment), so this is the server-data-provider shape. The rig and
 * owner lines are gated on {@code enableBatchBrewing}; brew progress is honest vanilla state.
 */
public final class BrewingStandProbeTooltip {

    static final String KEY_PRESENT = "distillation:present";
    static final String KEY_BREW_PROGRESS = "distillation:brewProgress";
    static final String KEY_RIGGED = "distillation:rigged";
    static final String KEY_WATER = "distillation:water";
    static final String KEY_MAX_WATER = "distillation:maxWater";
    static final String KEY_HEAT = "distillation:heat";
    static final String KEY_OWNER = "distillation:owner";

    // Vanilla's BrewingStandBlockEntity.BREW_TIME (private) — a cycle counts down from this to 0.
    private static final int BREW_TIME_TOTAL = 400;

    private BrewingStandProbeTooltip() {
    }

    /** Server side: writes nothing (no presence flag → empty tooltip) unless there is something to say. */
    public static void writeServerData(CompoundTag tag, ServerLevel level, BlockPos pos, BlockEntity be) {
        if (!(be instanceof BrewingStandBlockEntity stand)) {
            return;
        }
        int brewTime = ((BatchStand) stand).distillation$brewTime();
        boolean brewing = brewTime > 0;
        boolean batchEnabled = Distillation.getConfig().enableBatchBrewing;
        BatchRig.Status rig = batchEnabled ? BatchRig.detect(level, pos) : null;
        boolean rigged = rig != null && rig.rigged();
        Optional<UUID> owner = batchEnabled ? BatchStates.owner(stand) : Optional.empty();
        if (!brewing && !rigged && owner.isEmpty()) {
            return; // an idle, unrigged, unowned stand has no Distillation line
        }
        tag.putBoolean(KEY_PRESENT, true);
        if (brewing) {
            tag.putInt(KEY_BREW_PROGRESS, (BREW_TIME_TOTAL - brewTime) * 100 / BREW_TIME_TOTAL);
        }
        if (rigged) {
            tag.putBoolean(KEY_RIGGED, true);
            tag.putInt(KEY_WATER, rig.waterLevel());
            tag.putInt(KEY_MAX_WATER, rig.maxWater());
            if (rig.heat() != null) {
                tag.putString(KEY_HEAT, rig.heat().translationKey());
            }
        }
        owner.ifPresent(uuid -> tag.putString(KEY_OWNER, ownerName(level, uuid)));
    }

    /** Client side: the tag is data, not trusted state — every read falls back gracefully. */
    public static List<Component> buildLines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>();
        if (tag == null || !tag.getBoolean(KEY_PRESENT)) {
            return lines;
        }
        if (tag.contains(KEY_BREW_PROGRESS)) {
            lines.add(Component.translatable("tooltip.distillation.probe.brewing", tag.getInt(KEY_BREW_PROGRESS)));
        }
        if (tag.getBoolean(KEY_RIGGED)) {
            lines.add(Component.translatable("tooltip.distillation.probe.rigged",
                    tag.getInt(KEY_WATER), tag.getInt(KEY_MAX_WATER)));
            if (tag.contains(KEY_HEAT)) {
                lines.add(Component.translatable("tooltip.distillation.probe.heat",
                        Component.translatable(tag.getString(KEY_HEAT))));
            }
        }
        if (tag.contains(KEY_OWNER)) {
            lines.add(Component.translatable("tooltip.distillation.probe.owner", tag.getString(KEY_OWNER)));
        }
        return lines;
    }

    /** The owner's name: the online player, else the profile cache, else a short id — never blank. */
    private static String ownerName(ServerLevel level, UUID uuid) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var cache = level.getServer().getProfileCache();
        if (cache != null) {
            Optional<String> cached = cache.get(uuid).map(GameProfile::getName);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        return uuid.toString().substring(0, 8);
    }
}
