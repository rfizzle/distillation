package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.api.DistillationBrewCallback;
import com.rfizzle.distillation.api.DistillationDiscoveryCallback;
import com.rfizzle.distillation.brew.Antidotes;
import com.rfizzle.distillation.discovery.DiscoveryManager;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The targeted antidotes of {@code design/SPEC.md} §6 on a live server: each brews on a Thick base
 * from its own source (Awkward is an invalid pair), a drink strips exactly one affliction and leaves
 * the rest, an absent target consumes silently, splash cures a hit entity, a lingering cloud cleanses
 * an entity standing in it, the reagent rejects redstone/glowstone, milk still clears everything, and
 * the {@code enableAntidotes=false} toggle removes the lines while existing bottles keep working. The
 * Public API's brew and discovery callbacks fire from their server-side seams.
 *
 * <p>The lingering cloud carries vanilla's own duration and radius: SPEC §7's cloud rebalance
 * (1200 ticks / 4.5 radius) is a separate feature, so this suite asserts the cure, not the numbers.
 */
public class AntidoteGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 500;

    private static final AtomicBoolean ANTIDOTE_BREW_SEEN = new AtomicBoolean(false);

    // --- Brewing: Thick + reagent, and the invalid Awkward pair ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void poisonAntidoteBrewsFromThickAndFermentedSpiderEye(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper, thickBottle(), new ItemStack(Items.FERMENTED_SPIDER_EYE));
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), "distillation:poison_antidote");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void awkwardPlusReagentMurksNotAntidote(GameTestHelper helper) {
        // Thick is the antidote base; the same reagent on Awkward is an empty slot → Murky Draught.
        BrewingStandBlockEntity stand = placeStand(helper, vanillaBottle(Potions.AWKWARD),
                new ItemStack(Items.FERMENTED_SPIDER_EYE));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(stand.getItem(0).is(DistillationItems.MURKY_DRAUGHT),
                    "Awkward + Fermented Spider Eye must murk, not brew an antidote");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void antidoteRejectsRedstone(GameTestHelper helper) {
        // An antidote is a finished cure: no redstone/glowstone extension (invalid pair → Murky).
        BrewingStandBlockEntity stand = placeStand(helper, antidoteBottle("poison_antidote"),
                new ItemStack(Items.REDSTONE));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(stand.getItem(0).is(DistillationItems.MURKY_DRAUGHT),
                    "an antidote + redstone must murk, not extend");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyAntidoteConversionIsInTheGraph(GameTestHelper helper) {
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        for (Antidotes.BuiltIn builtIn : Antidotes.BUILTINS) {
            String reagent = BuiltInRegistries.ITEM.getKey(builtIn.reagent()).getPath();
            helper.assertTrue(graph.contains(ResourceLocation.fromNamespaceAndPath("distillation", reagent + "/thick")),
                    "graph must hold the Thick conversion for " + builtIn.path());
        }
        helper.succeed();
    }

    // --- Drinking: strips exactly the target, keeps everything else ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drinkingStripsOnlyTheTargetEffect(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));

            ItemStack antidote = antidoteBottle("poison_antidote");
            antidote.getItem().finishUsingItem(antidote, helper.getLevel(), player);

            helper.assertTrue(!player.hasEffect(MobEffects.POISON), "the poison antidote must strip Poison");
            helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST) && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "an antidote must leave every other effect untouched (surgical)");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void absentTargetConsumesSilently(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));

            ItemStack antidote = antidoteBottle("poison_antidote");
            ItemStack returned = antidote.getItem().finishUsingItem(antidote, helper.getLevel(), player);

            helper.assertTrue(!player.hasEffect(MobEffects.POISON), "no poison to strip — and none added");
            helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST),
                    "drinking with the target absent touches nothing else");
            helper.assertTrue(returned.is(Items.GLASS_BOTTLE), "the bottle is still consumed (the fizz plays either way)");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // --- Splash cures a hit entity; lingering cloud cleanses one standing in it ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void splashAntidoteCuresAPoisonedEntity(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        helper.assertTrue(cow.hasEffect(MobEffects.POISON), "the cow starts poisoned");

        applyAsSplash(cow, antidoteStack(Items.SPLASH_POTION, "poison_antidote"));

        helper.assertTrue(!cow.hasEffect(MobEffects.POISON), "a splash antidote must cure a hit entity's Poison");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void lingeringAntidoteCloudCleansesEntitiesWithin(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 600));

        AreaEffectCloud cloud = helper.spawn(EntityType.AREA_EFFECT_CLOUD, new BlockPos(2, 2, 2));
        cloud.setPotionContents(antidoteStack(Items.LINGERING_POTION, "poison_antidote")
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY));
        cloud.setRadius(3.0F);
        cloud.setWaitTime(0); // apply on the next eligible tick rather than after vanilla's warm-up

        // Tick the real cloud over the poisoned cow; it applies its cleanse effect on the cadence.
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(cloud.isAlive(), "the cloud must still be cleansing within its lifetime");
            helper.assertTrue(!cow.hasEffect(MobEffects.POISON),
                    "a lingering antidote cloud must cleanse an entity standing in it");
            helper.succeed();
        });
    }

    // --- Milk is untouched: it still clears everything ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void milkStillClearsEverything(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600));

            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            milk.getItem().finishUsingItem(milk, helper.getLevel(), player);

            helper.assertTrue(!player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.DAMAGE_BOOST),
                    "milk must still clear every effect, buffs included");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // --- Rendering: per-cure tint and the clean cure tooltip ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void antidoteLiquidTintsWithTheCuredEffectColor(GameTestHelper helper) {
        int cure = antidoteBottle("poison_antidote")
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
        helper.assertTrue(cure == MobEffects.POISON.value().getColor(),
                "a Poison Antidote must tint with Poison's color: " + Integer.toHexString(cure));
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void antidoteTooltipShowsTheCureNotTheRawEffect(GameTestHelper helper) {
        List<Component> lines = antidoteBottle("poison_antidote")
                .getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL);
        helper.assertTrue(hasKey(lines, "tooltip.distillation.antidote"),
                "an antidote tooltip must carry the 'Cures X' line");
        helper.assertTrue(!hasKey(lines, "effect.distillation.cleanse"),
                "the internal cleanse effect line must be suppressed");
        helper.succeed();
    }

    // --- Public API observation events fire from their server-side seams ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void brewCallbackFiresForAnAntidoteBrew(GameTestHelper helper) {
        ANTIDOTE_BREW_SEEN.set(false);
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) -> {
            if (!batch && results.stream().anyMatch(AntidoteGameTest::isAntidoteStack)) {
                ANTIDOTE_BREW_SEEN.set(true);
            }
        });
        placeStand(helper, thickBottle(), new ItemStack(Items.FERMENTED_SPIDER_EYE));
        helper.runAfterDelay(BREW_WAIT, () -> {
            helper.assertTrue(ANTIDOTE_BREW_SEEN.get(),
                    "the brew callback must fire server-side with the antidote result");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void discoveryCallbackFiresOnceOnFirstDiscovery(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            AtomicInteger fired = new AtomicInteger();
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("distillation", "fermented_spider_eye/thick");
            DistillationDiscoveryCallback.EVENT.register((who, id) -> {
                if (who == player && id.equals(recipeId)) {
                    fired.incrementAndGet();
                }
            });
            DiscoveryManager.record(player, recipeId);
            DiscoveryManager.record(player, recipeId); // already known — must not fire again

            helper.assertTrue(fired.get() == 1,
                    "the discovery callback fires exactly once on first discovery, got " + fired.get());
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    // --- registerAntidote validation (the success path is init-only; the six built-ins cover it) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registerAntidoteRefusesDuplicatesAndUnknownEffects(GameTestHelper helper) {
        helper.assertTrue(!Antidotes.registerAntidote(BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.POISON.value()),
                        net.minecraft.world.item.crafting.Ingredient.of(Items.FERMENTED_SPIDER_EYE)),
                "registerAntidote must refuse an effect that already has an antidote");
        helper.assertTrue(!Antidotes.registerAntidote(ResourceLocation.fromNamespaceAndPath("distillation", "no_such_effect"),
                        net.minecraft.world.item.crafting.Ingredient.of(Items.FERMENTED_SPIDER_EYE)),
                "registerAntidote must refuse an id that names no registered effect");
        // The six built-ins registered cleanly, each with a distinct sequential index.
        for (int i = 0; i < Antidotes.BUILTINS.size(); i++) {
            helper.assertTrue(Antidotes.targetForIndex(i) != null, "antidote index " + i + " resolves a target");
        }
        helper.succeed();
    }

    // --- The toggle removes the lines while existing bottles keep working ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationAntidotesToggle")
    public void toggleOffRemovesAntidotesKeepsBottles(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enableAntidotes;
        Distillation.getConfig().enableAntidotes = false;
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:fermented_spider_eye/thick")),
                    "with enableAntidotes=false the antidote conversions leave the graph");
            helper.assertTrue(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")),
                    "vanilla conversions stay in the graph");
            helper.assertTrue(BuiltInRegistries.POTION.getOptional(Distillation.id("poison_antidote")).isPresent(),
                    "antidote potions stay registered while the toggle is off");
        } finally {
            Distillation.getConfig().enableAntidotes = saved;
        }
        helper.succeed();
    }

    // --- helpers ---

    private static ItemStack vanillaBottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static ItemStack thickBottle() {
        return PotionContents.createItemStack(Items.POTION, Potions.THICK);
    }

    private static Holder<Potion> antidote(String path) {
        return BuiltInRegistries.POTION
                .getHolder(ResourceKey.create(Registries.POTION, Distillation.id(path)))
                .orElseThrow();
    }

    private static ItemStack antidoteBottle(String path) {
        return PotionContents.createItemStack(Items.POTION, antidote(path));
    }

    private static ItemStack antidoteStack(Item item, String path) {
        return PotionContents.createItemStack(item, antidote(path));
    }

    private static boolean isAntidoteStack(ItemStack stack) {
        ResourceLocation id = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location)
                .orElse(null);
        return id != null && Antidotes.isAntidote(id);
    }

    /** Applies a splash potion's effects the way {@code ThrownPotion.applySplash} does per hit entity. */
    private static void applyAsSplash(LivingEntity target, ItemStack splashPotion) {
        PotionContents contents = splashPotion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.getEffect().value().isInstantenous()) {
                instance.getEffect().value().applyInstantenousEffect(null, null, target, instance.getAmplifier(), 1.0);
            } else {
                target.addEffect(new MobEffectInstance(instance));
            }
        }
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals(key));
    }

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, ItemStack bottle, ItemStack ingredient) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static void assertPotion(GameTestHelper helper, ItemStack stack, String expectedPotionId) {
        String actual = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(key -> key.location().toString())
                .orElse("<none>");
        helper.assertTrue(actual.equals(expectedPotionId),
                "expected bottle to hold " + expectedPotionId + " but found " + actual);
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
