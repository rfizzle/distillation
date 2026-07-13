package com.rfizzle.distillation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.batch.BatchRig;
import com.rfizzle.distillation.config.DistillationConfig;
import com.rfizzle.distillation.discovery.DiscoveryData;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * The {@code /distillation} command tree ({@code design/SPEC.md} §10). Reads about yourself are
 * open (perm 0); reads about others and every mutation are op-gated (perm 2) on the exact node
 * where privilege starts. All mutations route through {@link DiscoveryManager} so the client sync
 * fires exactly as it does in play, and all feedback is localized {@code command.distillation.*}.
 */
public final class DistillationCommand {

    /** How many recent discoveries the {@code recipes} report names. */
    static final int LATEST_COUNT = 5;

    private static final SuggestionProvider<CommandSourceStack> RECIPE_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(graph(context.getSource().getServer()).ids(), builder);

    private DistillationCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Distillation.MOD_ID)
                .then(Commands.literal("recipes")
                        .executes(ctx -> runRecipes(ctx.getSource(), ctx.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> runRecipes(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), true))))
                .then(Commands.literal("discover")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all")
                                .executes(ctx -> runDiscoverAll(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> runDiscoverAll(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                .suggests(RECIPE_IDS)
                                .executes(ctx -> runDiscover(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> runDiscover(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("forget")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all")
                                .executes(ctx -> runForgetAll(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> runForgetAll(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                .suggests(RECIPE_IDS)
                                .executes(ctx -> runForget(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> runForget(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("rig")
                        .executes(ctx -> runRig(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(DistillationCommand::runReload)));
    }

    private static int runRecipes(CommandSourceStack source, ServerPlayer target, boolean other) {
        RecipeGraph graph = graph(source.getServer());
        DiscoveryData data = DiscoveryManager.data(target);
        int count = data.discoveredCount(graph.ids());
        int total = graph.ids().size();

        if (other) {
            source.sendSuccess(() -> Component.translatable("command.distillation.recipes.count.other",
                    target.getDisplayName(), count, total), false);
        } else {
            source.sendSuccess(() -> Component.translatable("command.distillation.recipes.count",
                    count, total), false);
        }
        List<ResourceLocation> latest = data.latestDiscovered(graph.ids(), LATEST_COUNT);
        if (!latest.isEmpty()) {
            Component names = ComponentUtils.formatList(
                    latest.stream().map(id -> recipeDisplayName(graph, id)).toList(),
                    Component.literal(", "));
            source.sendSuccess(() -> Component.translatable("command.distillation.recipes.latest", names)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return count;
    }

    private static int runDiscover(CommandContext<CommandSourceStack> ctx, ServerPlayer target)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation recipeId = ResourceLocationArgument.getId(ctx, "recipe");
        RecipeGraph graph = graph(source.getServer());
        if (!graph.contains(recipeId)) {
            source.sendFailure(Component.translatable("command.distillation.unknown_recipe",
                    Component.literal(recipeId.toString())));
            return 0;
        }
        if (!DiscoveryManager.record(target, recipeId)) {
            source.sendFailure(Component.translatable("command.distillation.discover.already",
                    target.getDisplayName(), recipeDisplayName(graph, recipeId)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.distillation.discover",
                recipeDisplayName(graph, recipeId), target.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runDiscoverAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        int added = DiscoveryManager.discoverAll(target, graph(source.getServer()));
        source.sendSuccess(() -> Component.translatable("command.distillation.discover.all",
                added, target.getDisplayName()), true);
        return added;
    }

    private static int runForget(CommandContext<CommandSourceStack> ctx, ServerPlayer target)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation recipeId = ResourceLocationArgument.getId(ctx, "recipe");
        // recipeDisplayName falls back to the raw id for ids the graph no longer carries — the
        // stale entries forget must still reach.
        RecipeGraph graph = graph(source.getServer());
        if (!DiscoveryManager.forget(target, recipeId)) {
            source.sendFailure(Component.translatable("command.distillation.forget.not_found",
                    target.getDisplayName(), recipeDisplayName(graph, recipeId)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.distillation.forget",
                recipeDisplayName(graph, recipeId), target.getDisplayName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int runForgetAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        int removed = DiscoveryManager.forgetAll(target);
        source.sendSuccess(() -> Component.translatable("command.distillation.forget.all",
                removed, target.getDisplayName()), true);
        return removed;
    }

    /**
     * Reports the batch-rig status of the brewing stand the caller is looking at, within 10 blocks
     * ({@code design/SPEC.md} §10): the water level and heat source when rigged, or the first missing
     * piece top-down (cauldron, then water, then heat). All output is localized.
     */
    private static int runRig(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(10.0, 1.0F, false);
        ServerLevel level = player.serverLevel();
        if (hit.getType() != HitResult.Type.BLOCK
                || !(level.getBlockState(((BlockHitResult) hit).getBlockPos()).getBlock() instanceof BrewingStandBlock)) {
            source.sendFailure(Component.translatable("command.distillation.rig.no_stand"));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BatchRig.Status status = BatchRig.detect(level, pos);
        switch (status.piece()) {
            case RIGGED -> source.sendSuccess(() -> Component.translatable("command.distillation.rig.status",
                    status.waterLevel(), status.maxWater(),
                    Component.translatable(status.heat().translationKey())), false);
            case NO_CAULDRON -> source.sendSuccess(
                    () -> Component.translatable("command.distillation.rig.missing.cauldron"), false);
            case NO_WATER -> source.sendSuccess(
                    () -> Component.translatable("command.distillation.rig.missing.water"), false);
            case NO_HEAT -> source.sendSuccess(
                    () -> Component.translatable("command.distillation.rig.missing.heat"), false);
        }
        return status.rigged() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        try {
            Distillation.reloadConfig(server);
        } catch (Exception e) {
            Distillation.LOGGER.error("Config reload failed via command", e);
            source.sendFailure(Component.translatable("command.distillation.reload_failed",
                    String.valueOf(e.getMessage())));
            return 0;
        }
        // The cache key only tracks the enableMissingBrews value, so force the rebuild the spec
        // promises ("reloads the JSON config and rebuilds the recipe graph") even when the reload
        // changed nothing else.
        RecipeGraphs.invalidate();
        int conversions = graph(server).conversions().size();
        source.sendSuccess(() -> Component.translatable("command.distillation.reload", conversions), true);
        return Command.SINGLE_SUCCESS;
    }

    private static RecipeGraph graph(MinecraftServer server) {
        DistillationConfig config = Distillation.getConfig();
        return RecipeGraphs.lookup(server.potionBrewing(), config.enableMissingBrews, config.enablePremiumBrews,
                config.enableAntidotes);
    }

    /**
     * A recipe's player-readable name: its output. Potion conversions name the drinkable form
     * ("Potion of Haste") — the input bottle's item isn't knowable from the id alone; container
     * conversions name the container they produce ("Splash Potion").
     */
    private static Component recipeDisplayName(RecipeGraph graph, ResourceLocation recipeId) {
        return graph.conversionById(recipeId).map(conversion -> {
            if (conversion instanceof RecipeGraph.PotionConversion potion) {
                return PotionContents.createItemStack(Items.POTION, potion.to()).getHoverName();
            }
            return (Component) ((RecipeGraph.ContainerConversion) conversion).to().getDescription();
        }).orElseGet(() -> Component.literal(recipeId.toString()));
    }
}
