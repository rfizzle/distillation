package com.rfizzle.distillation.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.util.UUID;

/**
 * A connected {@link ServerPlayer} for gametests — the faithful replica of vanilla's
 * {@code GameTestHelper#makeMockServerPlayerInLevel()}, which is deprecated for removal with no
 * replacement. The player has a live {@code connection} (an {@link EmbeddedChannel} absorbs sent
 * packets), is registered in the player list, and lives in the level — so Fabric attachments,
 * packet sends, and real-vs-fake player classification all behave as they would for a genuine
 * player. Spawns near world spawn, not in the test structure: teleport before proximity work.
 */
public final class MockPlayers {

    private MockPlayers() {
    }

    public static ServerPlayer serverPlayerInLevel(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // Vanilla's mock forces these two overrides; a bare ServerPlayer would report
        // spectator/non-creative and silently change gameplay-gated behavior.
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // absorbs sent packets; no real client
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
