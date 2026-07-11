package com.rfizzle.distillation.discovery;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.network.DistillationNetworking;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import com.rfizzle.distillation.sound.DistillationSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

/**
 * The write choke point for per-player recipe discovery ({@code design/SPEC.md} §1): the
 * extraction hook, the join grant, and the {@code /distillation} admin verbs all mutate through
 * here, so the config kill switch, the attachment write, and the client sync can never drift
 * apart. All methods are server-thread only.
 */
public final class DiscoveryManager {

    private DiscoveryManager() {
    }

    /**
     * The output-slot extraction hook ({@code BrewingStandMenu$PotionSlot#onTake}): consumes the
     * slot's brew provenance and records the discovery for the taking player. Hoppers never pass
     * through {@code onTake}, so automation teaches nobody by construction; fake players are
     * classified out explicitly.
     */
    public static void onOutputTaken(Player player, Container container, int slot, ItemStack taken) {
        if (!(player instanceof ServerPlayer serverPlayer) || FakePlayers.isFakePlayer(serverPlayer)) {
            return; // client-side menu copies use a SimpleContainer, so this is server-only in practice too
        }
        if (!(container instanceof BrewingStandBlockEntity stand) || stand.getLevel() == null) {
            return;
        }
        BrewProvenances.take(stand, slot).ifPresent(recipeId -> {
            Level level = stand.getLevel();
            if (matchesRecordedOutput(RecipeGraphs.forLevel(level), recipeId, taken)
                    && record(serverPlayer, recipeId)) {
                celebrate(serverPlayer, taken);
            }
        });
    }

    /**
     * The first-time teaching moment ({@code design/SPEC.md} §1): the ✦ action-bar toast naming
     * the output, and the discovery chime sent straight to the player's connection — a level
     * broadcast would turn one player's discovery into an area-wide mystery noise. Extraction
     * only: command and join grants record silently.
     */
    private static void celebrate(ServerPlayer player, ItemStack taken) {
        player.displayClientMessage(Component.translatable(
                "notification.distillation.recipe_learned", taken.getHoverName()), true);
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(DistillationSounds.RECIPE_LEARNED),
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f, player.getRandom().nextLong()));
    }

    /**
     * Records one discovery; {@code true} only when it is new for this player. Silent no-op while
     * {@code enableDiscovery=false} (the server-authoritative kill switch). A first-time discovery
     * is pushed to the owner's client immediately.
     */
    public static boolean record(ServerPlayer player, ResourceLocation recipeId) {
        if (!Distillation.getConfig().enableDiscovery) {
            return false;
        }
        boolean added = player.getAttachedOrCreate(DistillationAttachments.DISCOVERY).add(recipeId);
        if (added) {
            DistillationNetworking.sendDiscoveryAdded(player, recipeId);
        }
        return added;
    }

    /** Grants every recipe in the graph ({@code /distillation discover all}); returns how many were new. */
    public static int discoverAll(ServerPlayer player, RecipeGraph graph) {
        if (!Distillation.getConfig().enableDiscovery) {
            return 0;
        }
        int added = player.getAttachedOrCreate(DistillationAttachments.DISCOVERY)
                .addAll(List.copyOf(graph.ids()));
        if (added > 0) {
            DistillationNetworking.sendDiscoverySet(player);
        }
        return added;
    }

    /** Removes one discovery ({@code /distillation forget}); {@code true} when it was stored. */
    public static boolean forget(ServerPlayer player, ResourceLocation recipeId) {
        boolean removed = player.getAttachedOrCreate(DistillationAttachments.DISCOVERY).remove(recipeId);
        if (removed) {
            DistillationNetworking.sendDiscoverySet(player);
        }
        return removed;
    }

    /** Removes every discovery ({@code /distillation forget all}); returns how many were stored. */
    public static int forgetAll(ServerPlayer player) {
        int removed = player.getAttachedOrCreate(DistillationAttachments.DISCOVERY).clear();
        if (removed > 0) {
            DistillationNetworking.sendDiscoverySet(player);
        }
        return removed;
    }

    /**
     * The join hook: applies the {@code startDiscovered} grant (idempotent — a set already
     * complete gains nothing), then pushes the player's full discovery set so the client starts
     * in sync.
     */
    public static void onJoin(ServerPlayer player) {
        if (Distillation.getConfig().enableDiscovery && Distillation.getConfig().startDiscovered) {
            RecipeGraph graph = RecipeGraphs.forLevel(player.serverLevel());
            player.getAttachedOrCreate(DistillationAttachments.DISCOVERY).addAll(List.copyOf(graph.ids()));
        }
        DistillationNetworking.sendDiscoverySet(player);
    }

    /** The player's discovery data, read-only surfaces included ({@code /distillation recipes}). */
    public static DiscoveryData data(ServerPlayer player) {
        return player.getAttachedOrCreate(DistillationAttachments.DISCOVERY);
    }

    /**
     * Verifies the taken stack still matches what the recorded conversion produced — the guard
     * against a foreign bottle placed over a hopper-drained provenance slot. A recipe id the
     * current graph no longer carries verifies trivially: the brew genuinely happened, and stale
     * ids are legitimate stored state.
     */
    private static boolean matchesRecordedOutput(RecipeGraph graph, ResourceLocation recipeId, ItemStack taken) {
        return graph.conversionById(recipeId).map(conversion -> {
            if (conversion instanceof RecipeGraph.ContainerConversion container) {
                return taken.is(container.to());
            }
            RecipeGraph.PotionConversion potion = (RecipeGraph.PotionConversion) conversion;
            return taken.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .potion()
                    .map(held -> potion.to().is(held))
                    .orElse(false);
        }).orElse(true);
    }
}
