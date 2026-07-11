package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.MurkyDraughtContents;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Murky Draught contract of {@code design/SPEC.md} §1 on a live server: a mixed pass converts
 * valid bottles and murks invalid ones in one cycle with one agreed hint; a no-onward input
 * bottles hintless; drinking applies Nausea 0:15 plus the flicker, returns the glass bottle, and
 * teaches nothing; draughts are inert to further brewing; and the kill switch restores the
 * valid-pair gate with invalid bottles passing through.
 */
public class MurkyDraughtGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 600;
    private static final int NAUSEA_TICKS = 300;
    private static final int FLICKER_CAP_TICKS = 400;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void mixedPassConvertsValidAndMurksInvalidWithOneHint(GameTestHelper helper) {
        // Nether wart takes on water (slot 0) but not on awkward (slots 1–2).
        BrewingStandBlockEntity stand = placeStand(helper,
                bottle(Items.POTION, "minecraft:water"),
                new ItemStack(Items.NETHER_WART));
        stand.setItem(1, bottle(Items.POTION, "minecraft:awkward"));
        stand.setItem(2, bottle(Items.POTION, "minecraft:awkward"));

        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(stand.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .is(Potions.AWKWARD), "the valid pair must still brew in a mixed pass");

            MurkyDraughtContents first = draughtContents(helper, stand.getItem(1));
            MurkyDraughtContents second = draughtContents(helper, stand.getItem(2));
            helper.assertTrue(first.inputPotion().equals(ResourceLocation.parse("minecraft:awkward")),
                    "the draught must record the potion it came from");
            helper.assertTrue(first.hintIngredient().isPresent(),
                    "awkward has onward conversions, so the draught must carry a hint");
            helper.assertTrue(first.hintIngredient().equals(second.hintIngredient()),
                    "all draughts of one pass must agree on the hint");

            Set<ResourceLocation> validIngredients = RecipeGraphs.forLevel(helper.getLevel())
                    .conversionsFor(bottle(Items.POTION, "minecraft:awkward")).stream()
                    .map(conversion -> BuiltInRegistries.ITEM.getKey(conversion.ingredient()))
                    .collect(Collectors.toSet());
            helper.assertTrue(validIngredients.contains(first.hintIngredient().get()),
                    "the hint must be a conversion that genuinely would have taken");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void noOnwardInputBottlesHintless(GameTestHelper helper) {
        // Nothing brews onward from a lingering bottle of Strong Harming: no potion mix leaves
        // strong_harming, and no container conversion leaves the lingering bottle.
        BrewingStandBlockEntity stand = placeStand(helper,
                bottle(Items.LINGERING_POTION, "minecraft:strong_harming"),
                new ItemStack(Items.NETHER_WART));

        helper.runAfterDelay(BREW_WAIT, () -> {
            MurkyDraughtContents contents = draughtContents(helper, stand.getItem(0));
            helper.assertTrue(contents.inputPotion().equals(ResourceLocation.parse("minecraft:strong_harming")),
                    "the hintless draught still records its input potion");
            helper.assertTrue(contents.hintIngredient().isEmpty(),
                    "an input with no onward conversions must yield the hintless draught");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drinkingAppliesNauseaAndFlickerAndTeachesNothing(GameTestHelper helper) {
        // Hint at Awkward + Shulker Shell -> distillation:resistance (base duration 3:00, so the
        // 0:20 flicker cap is what actually limits it).
        ItemStack draught = draught("minecraft:awkward", Optional.of("minecraft:shulker_shell"));
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack returned = draught.getItem().finishUsingItem(draught, helper.getLevel(), player);

            MobEffectInstance nausea = player.getEffect(MobEffects.CONFUSION);
            helper.assertTrue(nausea != null && nausea.getAmplifier() == 0
                            && nausea.getDuration() <= NAUSEA_TICKS && nausea.getDuration() > NAUSEA_TICKS - 10,
                    "drinking must apply Nausea 0:15 at amplifier 0");

            MobEffectInstance flicker = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(flicker != null && flicker.getAmplifier() == 0,
                    "the flicker must apply the hinted output's effect at amplifier 0");
            helper.assertTrue(flicker.getDuration() <= FLICKER_CAP_TICKS,
                    "the flicker's duration must cap at 0:20 (400 ticks)");

            helper.assertTrue(returned.is(Items.GLASS_BOTTLE), "the glass bottle must come back");
            helper.assertTrue(DiscoveryManager.data(player).orderedIds().isEmpty(),
                    "the flicker must never record discovery");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hintlessDrinkIsNauseaAlone(GameTestHelper helper) {
        ItemStack draught = draught("minecraft:strong_harming", Optional.empty());
        ServerPlayer player = survivalPlayer(helper);
        try {
            ItemStack returned = draught.getItem().finishUsingItem(draught, helper.getLevel(), player);

            helper.assertTrue(player.getEffect(MobEffects.CONFUSION) != null,
                    "the hintless draught still nauseates");
            helper.assertTrue(player.getActiveEffects().size() == 1,
                    "the hintless draught applies nausea and nothing else");
            helper.assertTrue(returned.is(Items.GLASS_BOTTLE), "the glass bottle must come back");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void murkyDraughtIsInertToFurtherBrewing(GameTestHelper helper) {
        ItemStack draught = draught("minecraft:awkward", Optional.of("minecraft:shulker_shell"));
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        helper.assertTrue(!stand.canPlaceItem(0, draught),
                "a Murky Draught must be rejected by the bottle slots");

        // Even forced into a slot (a rogue mod or command), it is not receptive: no cycle starts.
        stand.setItem(0, draught);
        stand.setItem(3, new ItemStack(Items.NETHER_WART));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(stand.getItem(0).is(DistillationItems.MURKY_DRAUGHT),
                    "a Murky Draught must never brew onward");
            helper.assertTrue(stand.getItem(3).getCount() == 1,
                    "no cycle may start over a draught-only stand");
            helper.succeed();
        });
    }

    /** Own batch: flips the live server config, so it must never overlap tests running under defaults. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationMurkyOff", timeoutTicks = TIMEOUT)
    public void killSwitchRestoresTheValidPairGate(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableMurkyDraughts;
        Distillation.getConfig().enableMurkyDraughts = false;

        // Invalid pair alone: the cycle must not even start.
        BrewingStandBlockEntity invalidOnly = placeStand(helper,
                bottle(Items.POTION, "minecraft:awkward"), new ItemStack(Items.NETHER_WART));
        // Mixed pass: the valid bottle carries the cycle, the invalid one passes through unbrewed.
        BlockPos mixedPos = new BlockPos(3, 2, 1);
        helper.setBlock(mixedPos, Blocks.BREWING_STAND);
        BrewingStandBlockEntity mixed = helper.getBlockEntity(mixedPos);
        mixed.setItem(0, bottle(Items.POTION, "minecraft:water"));
        mixed.setItem(1, bottle(Items.POTION, "minecraft:awkward"));
        mixed.setItem(3, new ItemStack(Items.NETHER_WART));
        mixed.setItem(4, new ItemStack(Items.BLAZE_POWDER));

        helper.runAfterDelay(BREW_WAIT, () -> {
            try {
                helper.assertTrue(invalidOnly.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS,
                                PotionContents.EMPTY).is(Potions.AWKWARD),
                        "with draughts off, an invalid pair must not brew");
                helper.assertTrue(invalidOnly.getItem(3).getCount() == 1,
                        "with draughts off, a cycle needs at least one valid pair to start");

                helper.assertTrue(mixed.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS,
                                PotionContents.EMPTY).is(Potions.AWKWARD),
                        "the valid pair still brews in the mixed pass");
                helper.assertTrue(mixed.getItem(1).getOrDefault(DataComponents.POTION_CONTENTS,
                                PotionContents.EMPTY).is(Potions.AWKWARD)
                                && mixed.getItem(1).is(Items.POTION),
                        "with draughts off, the invalid bottle passes through unbrewed — never murked");
                helper.succeed();
            } finally {
                Distillation.getConfig().enableMurkyDraughts = saved;
            }
        });
    }

    // --- helpers ---

    private static ItemStack bottle(Item item, String potionId) {
        var potion = BuiltInRegistries.POTION
                .getHolder(ResourceKey.create(Registries.POTION, ResourceLocation.parse(potionId)))
                .orElseThrow();
        return PotionContents.createItemStack(item, potion);
    }

    private static ItemStack draught(String inputPotion, Optional<String> hintIngredient) {
        ItemStack stack = new ItemStack(DistillationItems.MURKY_DRAUGHT);
        stack.set(DistillationItems.MURKY_DRAUGHT_CONTENTS, new MurkyDraughtContents(
                ResourceLocation.parse(inputPotion), hintIngredient.map(ResourceLocation::parse)));
        return stack;
    }

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, ItemStack bottle, ItemStack ingredient) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static MurkyDraughtContents draughtContents(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(stack.is(DistillationItems.MURKY_DRAUGHT),
                "expected a Murky Draught but found " + stack);
        MurkyDraughtContents contents = stack.get(DistillationItems.MURKY_DRAUGHT_CONTENTS);
        helper.assertTrue(contents != null, "a bottled draught must carry its contents component");
        return contents;
    }

    /**
     * A connected survival-mode player: effects need a live connection ({@code onEffectAdded}
     * sends a packet), and drinking needs consumption — survival abilities make
     * {@code hasInfiniteMaterials()} false, so the stack shrinks and the bottle comes back.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
