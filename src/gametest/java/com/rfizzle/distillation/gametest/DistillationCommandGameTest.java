package com.rfizzle.distillation.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /distillation} command surface ({@code design/SPEC.md} §10) on a live dispatcher:
 * per-node permission gating, recipe-id suggestions, and the discover/forget/recipes/reload verbs
 * mutating real player state through the manager.
 */
public class DistillationCommandGameTest implements FabricGameTest {

    private static final String VANILLA_RECIPE = "distillation:nether_wart/water";

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void permissionGatesSitOnTheRightNodes(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot()
                .getChild("distillation");
        helper.assertTrue(root != null, "/distillation must be registered");

        CommandSourceStack nonOp = helper.getLevel().getServer()
                .createCommandSourceStack().withPermission(0);
        CommandSourceStack op = helper.getLevel().getServer()
                .createCommandSourceStack().withPermission(2);

        helper.assertTrue(root.getChild("recipes").canUse(nonOp), "recipes (self) must be open to everyone");
        helper.assertTrue(!root.getChild("recipes").getChild("player").canUse(nonOp),
                "recipes <player> must deny non-ops");
        helper.assertTrue(root.getChild("recipes").getChild("player").canUse(op),
                "recipes <player> must allow ops");
        for (String verb : new String[]{"discover", "forget", "reload"}) {
            helper.assertTrue(!root.getChild(verb).canUse(nonOp), verb + " must deny non-ops");
            helper.assertTrue(root.getChild(verb).canUse(op), verb + " must allow ops");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoverSuggestsGraphRecipeIds(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack op = helper.getLevel().getServer()
                .createCommandSourceStack().withPermission(2);

        ParseResults<CommandSourceStack> parse = dispatcher.parse("distillation discover ", op);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
        boolean found = suggestions.getList().stream()
                .anyMatch(s -> s.getText().equals(VANILLA_RECIPE));
        helper.assertTrue(found, "recipe argument must suggest graph ids, got: "
                + suggestions.getList().size() + " suggestions");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoverForgetRoundTripMutatesRealState(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            CommandSourceStack op = player.createCommandSourceStack().withPermission(2);
            var commands = helper.getLevel().getServer().getCommands();
            ResourceLocation recipe = ResourceLocation.parse(VANILLA_RECIPE);

            commands.performPrefixedCommand(op, "distillation discover " + VANILLA_RECIPE);
            helper.assertTrue(DiscoveryManager.data(player).contains(recipe),
                    "discover must record through the manager");

            commands.performPrefixedCommand(op, "distillation discover distillation:not_a_recipe/bogus");
            helper.assertTrue(DiscoveryManager.data(player).orderedIds().size() == 1,
                    "an id outside the graph must not be granted");

            commands.performPrefixedCommand(op, "distillation forget " + VANILLA_RECIPE);
            helper.assertTrue(!DiscoveryManager.data(player).contains(recipe),
                    "forget must remove the discovery");

            commands.performPrefixedCommand(op, "distillation discover all");
            var graphIds = RecipeGraphs.forLevel(helper.getLevel()).ids();
            helper.assertTrue(DiscoveryManager.data(player).discoveredCount(graphIds) == graphIds.size(),
                    "discover all must complete the set");

            commands.performPrefixedCommand(op, "distillation forget all");
            helper.assertTrue(DiscoveryManager.data(player).orderedIds().isEmpty(),
                    "forget all must clear the set");

            commands.performPrefixedCommand(op, "distillation recipes");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void reloadRebuildsTheGraph(GameTestHelper helper) {
        CommandSourceStack op = helper.getLevel().getServer()
                .createCommandSourceStack().withPermission(2);
        var before = RecipeGraphs.forLevel(helper.getLevel());

        helper.getLevel().getServer().getCommands().performPrefixedCommand(op, "distillation reload");

        var after = RecipeGraphs.forLevel(helper.getLevel());
        helper.assertTrue(after != before, "reload must invalidate the cached graph, not serve it back");
        helper.assertTrue(after.contains(ResourceLocation.parse(VANILLA_RECIPE)),
                "the rebuilt graph must hold the same conversions");
        helper.succeed();
    }
}
