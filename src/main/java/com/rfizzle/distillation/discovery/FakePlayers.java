package com.rfizzle.distillation.discovery;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Classifies automation stand-ins (Fabric {@link FakePlayer}s, other mods' synthetic players,
 * bare-constructed {@code ServerPlayer}s) as non-players, so a machine with a player face can't
 * farm player-facing grants like recipe discovery. No single check suffices — the three probes
 * each catch a different fake-player flavor.
 */
public final class FakePlayers {

    private FakePlayers() {
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return true; // Fabric's fake player carries a synthetic non-null connection — the next check misses it
        }
        if (player.connection == null) {
            return true; // other implementations (and direct new ServerPlayer(...)) have no network handler
        }
        MinecraftServer server = player.getServer();
        // A genuine player is always in the player list; a fake never is, even when it borrows a
        // real player's profile — identity comparison, not UUID lookup.
        return server == null || !server.getPlayerList().getPlayers().contains(player);
    }
}
