package com.rfizzle.distillation.item;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.network.CopyRecipeNotePayload;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * The server side of copying a recipe onto paper ({@code design/SPEC.md} §1): the C2S receiver and
 * the authoritative copy. Every check the client's recipes-page button makes is re-made here — the
 * feature toggle, the recipe's presence in the live graph, the player's own discovery set, and paper
 * in hand — because the client is never trusted ({@code mc-networking}). The copy is a plain O(1)
 * inventory op self-limited by its paper cost, so no per-player cooldown is warranted. It never
 * records discovery: a note points, the stand teaches.
 */
public final class RecipeNoteServerHandler {

    private RecipeNoteServerHandler() {
    }

    /** Registers the C2S receiver; called from {@code onInitialize} after the payload type registers. */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CopyRecipeNotePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ResourceLocation recipeId = payload.recipeId();
            // Hop off the netty thread before touching inventory or world state (mc-networking).
            player.server.execute(() -> tryCopy(player, recipeId));
        });
    }

    /**
     * The authoritative copy, re-validating every input server-side. Returns empty on success (one
     * paper consumed, one note given), or the reason it refused — exposed so a gametest can drive the
     * copy directly and assert the outcome. A refusal is silent to the player: the client only ever
     * surfaces the button for a recipe it believes copyable, so a mismatch means stale client state,
     * not a player action to explain.
     */
    public static Optional<RecipeNotes.Denial> tryCopy(ServerPlayer player, ResourceLocation recipeId) {
        boolean enabled = Distillation.getConfig().enableRecipeNotes;
        RecipeGraph graph = RecipeGraphs.forLevel(player.serverLevel());
        boolean inGraph = graph.contains(recipeId);
        boolean discovered = DiscoveryManager.data(player).contains(recipeId);
        int paperSlot = firstPaperSlot(player.getInventory());

        Optional<RecipeNotes.Denial> denial =
                RecipeNotes.denial(enabled, inGraph, discovered, paperSlot >= 0);
        if (denial.isPresent()) {
            return denial;
        }

        player.getInventory().getItem(paperSlot).shrink(1);
        ItemStack note = RecipeNotes.createNote(recipeId);
        if (!player.getInventory().add(note)) {
            player.drop(note, false);
        }
        // Push the inventory change to the client — mirrors vanilla's /give.
        player.containerMenu.broadcastChanges();
        return Optional.empty();
    }

    /** The first inventory slot holding paper, or {@code -1} — the copy's material check and source. */
    private static int firstPaperSlot(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.PAPER)) {
                return slot;
            }
        }
        return -1;
    }
}
