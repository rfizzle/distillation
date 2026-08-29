package com.rfizzle.distillation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Guards {@link MockPlayers}' faithfulness to the vanilla connected-player construction
 * ({@code GameTestHelper#makeMockServerPlayerInLevel()}, deprecated for removal in 1.21.1 with no
 * replacement — see mc-testing-mock).
 *
 * <p>Every one of the five construction steps is load-bearing and every one of them fails
 * <em>silently</em> if dropped: a bare {@code new ServerPlayer(...)} has a null {@code connection}
 * (so every packet-sending path NPEs), is absent from the player list and from the level (so
 * attachments and real-vs-fake classification misread it), and reports spectator/non-creative (so
 * gameplay-gated behavior quietly changes). This suite makes a later "simplification" fail here
 * instead of in whichever connection-dependent suite happens to notice first.
 */
public class MockPlayersGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void connectedReplicaIsFaithful(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(player.connection != null,
                    "mock player has no ServerGamePacketListenerImpl — placeNewPlayer was not called");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "mock player is not registered in the player list");
            helper.assertTrue(player.level() == helper.getLevel(),
                    "mock player is not in the test level");
            helper.assertTrue(player.isCreative(),
                    "mock player must report creative like the vanilla helper");
            helper.assertTrue(!player.isSpectator(),
                    "mock player must not report as a spectator");
            helper.succeed();
        } finally {
            // Retired in a finally so a failing assertion above still reclaims the player rather
            // than leaking it into the shared gametest level for every later batch to trip over.
            if (!player.isRemoved()) {
                helper.getLevel().getServer().getPlayerList().remove(player);
                player.discard();
            }
        }
    }
}
