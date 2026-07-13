package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.RecipeNoteServerHandler;
import com.rfizzle.distillation.item.RecipeNotes;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * The recipe-note copy contract of {@code design/SPEC.md} §1 on a live server: a discovered recipe
 * with paper in hand copies to a note (one paper spent, discovery unchanged — a note points, it
 * never grants); an undiscovered recipe, a paperless hand, and the kill switch each refuse, leaving
 * the inventory untouched. The server-side {@link RecipeNoteServerHandler#tryCopy} is the seam the
 * C2S receiver drives, so exercising it directly is the faithful test of the packet path.
 */
public class RecipeNoteGameTest implements FabricGameTest {

    // Resistance from Awkward + Shulker Shell — a §2 missing brew, in the default graph.
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath(
            "distillation", "shulker_shell/awkward");

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoveredWithPaperCopiesToNoteWithoutGrantingDiscovery(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(RecipeGraphs.forLevel(helper.getLevel()).contains(RECIPE),
                    "the test recipe must be in the default graph");
            helper.assertTrue(DiscoveryManager.record(player, RECIPE), "seed the player's discovery");
            player.getInventory().add(new ItemStack(Items.PAPER, 3));

            Optional<RecipeNotes.Denial> outcome = RecipeNoteServerHandler.tryCopy(player, RECIPE);
            helper.assertTrue(outcome.isEmpty(), "a discovered recipe with paper must copy");

            ItemStack note = findNote(player);
            helper.assertTrue(!note.isEmpty(), "the copy must produce a recipe note");
            helper.assertTrue(RecipeNotes.notedRecipe(note).map(RECIPE::equals).orElse(false),
                    "the note must point at the copied recipe");
            helper.assertTrue(countPaper(player) == 2, "the copy must spend exactly one paper");
            helper.assertTrue(DiscoveryManager.data(player).orderedIds().size() == 1,
                    "copying must not record any new discovery — a note points, it never grants");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void undiscoveredRecipeIsRefusedAndPaperKept(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.getInventory().add(new ItemStack(Items.PAPER, 3));

            Optional<RecipeNotes.Denial> outcome = RecipeNoteServerHandler.tryCopy(player, RECIPE);
            helper.assertTrue(outcome.equals(Optional.of(RecipeNotes.Denial.NOT_DISCOVERED)),
                    "an unlearned recipe can't be copied — the stand still teaches first");
            helper.assertTrue(findNote(player).isEmpty(), "no note may be minted on refusal");
            helper.assertTrue(countPaper(player) == 3, "a refused copy must not spend paper");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoveredWithoutPaperIsRefused(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(DiscoveryManager.record(player, RECIPE), "seed the player's discovery");

            Optional<RecipeNotes.Denial> outcome = RecipeNoteServerHandler.tryCopy(player, RECIPE);
            helper.assertTrue(outcome.equals(Optional.of(RecipeNotes.Denial.NO_PAPER)),
                    "no paper, no copy");
            helper.assertTrue(findNote(player).isEmpty(), "no note without paper");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationNotesOff")
    public void killSwitchRefusesTheCopy(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableRecipeNotes;
        Distillation.getConfig().enableRecipeNotes = false;
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            DiscoveryManager.record(player, RECIPE);
            player.getInventory().add(new ItemStack(Items.PAPER, 3));

            Optional<RecipeNotes.Denial> outcome = RecipeNoteServerHandler.tryCopy(player, RECIPE);
            helper.assertTrue(outcome.equals(Optional.of(RecipeNotes.Denial.FEATURE_DISABLED)),
                    "with recipe notes off, the copy is refused server-side");
            helper.assertTrue(findNote(player).isEmpty() && countPaper(player) == 3,
                    "a disabled copy leaves the inventory untouched");
        } finally {
            Distillation.getConfig().enableRecipeNotes = saved;
            player.discard();
        }
        helper.succeed();
    }

    // --- helpers ---

    private static ItemStack findNote(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(DistillationItems.RECIPE_NOTE)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int countPaper(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.PAPER)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
