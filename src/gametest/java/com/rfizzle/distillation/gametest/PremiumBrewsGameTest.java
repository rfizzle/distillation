package com.rfizzle.distillation.gametest;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.brew.DistillationPotions;
import com.rfizzle.distillation.brew.PremiumBrews;
import com.rfizzle.distillation.brew.PremiumColors;
import com.rfizzle.distillation.item.DistillationItems;
import com.rfizzle.distillation.item.MurkyDraughtContents;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

/**
 * Concentrated &amp; premium brews on a real stand ({@code design/SPEC.md} §5): the concentration
 * step, both dust orders converging on the same premium bottle, the approved Slowness reagent, the
 * already-modified murk, the registered §5.3 stats, and the {@code enablePremiumBrews=false}
 * contract — the conversions leave the graph while existing bottles keep working.
 */
public class PremiumBrewsGameTest implements FabricGameTest {

    private static final BlockPos STAND = new BlockPos(1, 2, 1);
    private static final int BREW_WAIT = 420;
    private static final int TIMEOUT = 500;
    private static final int CHAIN_TIMEOUT = 1000;

    // --- Concentration and the single graph edges (Strength) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void strengthConcentrates(GameTestHelper helper) {
        assertBrewsTo(helper, vanillaBottle(Potions.STRENGTH), new ItemStack(Items.BLAZE_POWDER),
                "distillation:concentrated_strength");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void concentratedExtendsWithRedstone(GameTestHelper helper) {
        assertBrewsTo(helper, premiumBottle("concentrated_strength"), new ItemStack(Items.REDSTONE),
                "distillation:concentrated_long_strength");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void concentratedAmplifiesWithGlowstone(GameTestHelper helper) {
        assertBrewsTo(helper, premiumBottle("concentrated_strength"), new ItemStack(Items.GLOWSTONE_DUST),
                "distillation:concentrated_strong_strength");
    }

    // --- Both dust orders reach the same premium bottle (two real cycles each) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = CHAIN_TIMEOUT)
    public void redstoneThenGlowstoneBrewsPremium(GameTestHelper helper) {
        assertChainsToPremium(helper, Items.REDSTONE, "distillation:concentrated_long_strength", Items.GLOWSTONE_DUST);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = CHAIN_TIMEOUT)
    public void glowstoneThenRedstoneBrewsPremium(GameTestHelper helper) {
        assertChainsToPremium(helper, Items.GLOWSTONE_DUST, "distillation:concentrated_strong_strength", Items.REDSTONE);
    }

    // --- Slowness: the approved Fermented Spider Eye reagent, no vanilla collision ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void slownessConcentratesWithFermentedSpiderEye(GameTestHelper helper) {
        assertBrewsTo(helper, vanillaBottle(Potions.SLOWNESS), new ItemStack(Items.FERMENTED_SPIDER_EYE),
                "distillation:concentrated_slowness");
    }

    // --- Health Boost: a §2 line eligible for premium, mirroring Absorption (§5.1) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void healthBoostConcentrates(GameTestHelper helper) {
        assertBrewsTo(helper, distillationBottle("health_boost"), new ItemStack(Items.PUMPKIN_PIE),
                "distillation:concentrated_health_boost");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void premiumHealthBoostCarriesSpecStats(GameTestHelper helper) {
        // SPEC §5.3: Health Boost II 4:00 — the long variant (8:00) halved, at the strong amplifier.
        assertSingleEffect(helper, "premium_health_boost", 4800, 1);
        helper.succeed();
    }

    // --- Concentrating an already-modified potion is an invalid pair (→ Murky) ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TIMEOUT)
    public void concentratingModifiedPotionMurks(GameTestHelper helper) {
        BrewingStandBlockEntity stand = placeStand(helper, vanillaBottle(Potions.LONG_STRENGTH),
                new ItemStack(Items.BLAZE_POWDER));
        helper.runAfterDelay(BREW_WAIT, () -> {
            ItemStack murked = stand.getItem(0);
            helper.assertTrue(murked.is(DistillationItems.MURKY_DRAUGHT),
                    "concentrating a modified potion must murk, but found " + murked);
            MurkyDraughtContents contents = murked.get(DistillationItems.MURKY_DRAUGHT_CONTENTS);
            helper.assertTrue(contents != null && contents.inputPotion().toString().equals("minecraft:long_strength"),
                    "the draught must record the modified input potion");
            helper.succeed();
        });
    }

    // --- Registered §5.3 stats: identical concentrated, level-up premium ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void concentratedStrengthHasIdenticalStats(GameTestHelper helper) {
        // Base Strength is 3:00 at amplifier 0; the concentrated variant is identical.
        assertSingleEffect(helper, "concentrated_strength", 3600, 0);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void premiumStrengthCarriesSpecStats(GameTestHelper helper) {
        // SPEC §5.3: Strength II 4:00.
        assertSingleEffect(helper, "premium_strength", 4800, 1);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void premiumTurtleMasterKeepsStrongAmplifiers(GameTestHelper helper) {
        // SPEC §5.3: Turtle Master II 2:00 — the strong variant's two effects at the premium timer.
        // strong_turtle_master is vanilla; the premium output is Distillation's own registration.
        Potion strong = Potions.STRONG_TURTLE_MASTER.value();
        Potion premium = registered(helper, "premium_turtle_master");
        helper.assertTrue(premium.getEffects().size() == strong.getEffects().size(),
                "premium turtle master carries both of the strong variant's effects");
        for (int i = 0; i < premium.getEffects().size(); i++) {
            MobEffectInstance p = premium.getEffects().get(i);
            MobEffectInstance s = strong.getEffects().get(i);
            helper.assertTrue(p.getAmplifier() == s.getAmplifier(),
                    "premium keeps the strong amplifier for effect " + i);
            helper.assertTrue(p.getDuration() == 2400, "premium turtle master runs 2:00");
        }
        helper.succeed();
    }

    // --- The concentrated mark: deeper liquid + the Concentrated tooltip on every form ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void concentratedLiquidIsBaseColorDeepened(GameTestHelper helper) {
        int base = PotionContents.createItemStack(Items.POTION, Potions.STRENGTH)
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
        int concentrated = PotionContents.createItemStack(Items.POTION, PremiumBrews.potion("concentrated_strength"))
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
        helper.assertTrue(concentrated == PremiumColors.deepen(base),
                "concentrated liquid must be the base color deepened: base=" + Integer.toHexString(base)
                        + " concentrated=" + Integer.toHexString(concentrated));
        helper.assertTrue((concentrated & 0xFFFFFF) < (base & 0xFFFFFF),
                "concentrated liquid must render darker than the base");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void concentratedTagAppearsOnEveryPotionForm(GameTestHelper helper) {
        // Drink, splash, and lingering — the last overrides appendHoverText without super, so it is
        // a distinct mixin target. A premium bottle is in the family, so it carries the tag too.
        for (Item item : new Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
            ItemStack stack = PotionContents.createItemStack(item, PremiumBrews.potion("premium_strength"));
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null,
                    TooltipFlag.NORMAL);
            boolean tagged = lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("tooltip.distillation.concentrated"));
            helper.assertTrue(tagged, "the Concentrated tag must appear on " + BuiltInRegistries.ITEM.getKey(item));
        }
        helper.succeed();
    }

    // --- The graph carries the conversions; the toggle removes them ---

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void liveGraphHoldsConcentrationConversions(GameTestHelper helper) {
        RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
        assertContains(helper, graph, "distillation:blaze_powder/strength");
        assertContains(helper, graph, "distillation:redstone/distillation/concentrated_strength");
        assertContains(helper, graph, "distillation:glowstone_dust/distillation/concentrated_strength");
        assertContains(helper, graph, "distillation:glowstone_dust/distillation/concentrated_long_strength");
        assertContains(helper, graph, "distillation:redstone/distillation/concentrated_strong_strength");
        assertContains(helper, graph, "distillation:fermented_spider_eye/slowness");
        helper.succeed();
    }

    /**
     * Flips the live server config, so it resolves the contract <em>synchronously</em> — the graph
     * exclusion and the absence of the concentration conversion — and restores the flag in the same
     * invocation. A delayed brew here would race the concurrent {@code /distillation reload} test,
     * which re-reads the config from disk and would re-enable the feature mid-window.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "distillationPremiumToggle")
    public void toggleOffRemovesPremiumKeepsBottles(GameTestHelper helper) {
        boolean saved = Distillation.getConfig().enablePremiumBrews;
        Distillation.getConfig().enablePremiumBrews = false;
        try {
            RecipeGraph graph = RecipeGraphs.forLevel(helper.getLevel());
            helper.assertTrue(!graph.contains(ResourceLocation.parse("distillation:blaze_powder/strength")),
                    "with enablePremiumBrews=false the concentration conversions leave the graph");
            helper.assertTrue(graph.contains(ResourceLocation.parse("distillation:nether_wart/water")),
                    "vanilla conversions stay in the graph");
            helper.assertTrue(graph.contains(ResourceLocation.parse("distillation:shulker_shell/awkward")),
                    "the §2 lines stay in the graph (independent toggle)");
            // Strength + Blaze Powder no longer concentrates (it would murk instead — blaze powder
            // stays a graph ingredient, unlike a §2 ingredient when missing-brews is off).
            helper.assertTrue(graph.matchConversion(new ItemStack(Items.BLAZE_POWDER),
                            vanillaBottle(Potions.STRENGTH)) == null,
                    "with the toggle off, Strength + Blaze Powder no longer concentrates");
            // The premium potions stay registered and an existing bottle keeps its potion.
            helper.assertTrue(BuiltInRegistries.POTION.getOptional(Distillation.id("premium_strength")).isPresent(),
                    "premium potions stay registered while the toggle is off");
            helper.assertTrue(potionIdOf(premiumBottle("concentrated_strength")).equals("distillation:concentrated_strength"),
                    "an existing concentrated bottle keeps working");
        } finally {
            Distillation.getConfig().enablePremiumBrews = saved;
        }
        helper.succeed();
    }

    // --- helpers ---

    private static ItemStack vanillaBottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static ItemStack premiumBottle(String path) {
        return PotionContents.createItemStack(Items.POTION, PremiumBrews.potion(path));
    }

    /** A bottle of a §2 base potion (e.g. {@code distillation:health_boost}) — a premium line's base. */
    private static ItemStack distillationBottle(String path) {
        return PotionContents.createItemStack(Items.POTION, DistillationPotions.potion(path));
    }

    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, ItemStack bottle, ItemStack ingredient) {
        helper.setBlock(STAND, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
        stand.setItem(0, bottle);
        stand.setItem(3, ingredient);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    private static void assertBrewsTo(GameTestHelper helper, ItemStack bottle, ItemStack ingredient,
                                      String expectedPotionId) {
        BrewingStandBlockEntity stand = placeStand(helper, bottle, ingredient);
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), expectedPotionId);
            helper.succeed();
        });
    }

    /**
     * Brews concentrated Strength through both dusts in sequence — first {@code firstDust} to the
     * {@code intermediateId}, then {@code secondDust} — and asserts the same {@code premium_strength}
     * either order.
     */
    private static void assertChainsToPremium(GameTestHelper helper, net.minecraft.world.item.Item firstDust,
                                              String intermediateId, net.minecraft.world.item.Item secondDust) {
        BrewingStandBlockEntity stand = placeStand(helper, premiumBottle("concentrated_strength"),
                new ItemStack(firstDust));
        helper.runAfterDelay(BREW_WAIT, () -> {
            assertPotion(helper, stand.getItem(0), intermediateId);
            stand.setItem(3, new ItemStack(secondDust));
            if (stand.getItem(4).isEmpty()) {
                stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            }
            helper.runAfterDelay(BREW_WAIT, () -> {
                assertPotion(helper, stand.getItem(0), "distillation:premium_strength");
                helper.succeed();
            });
        });
    }

    private static Potion registered(GameTestHelper helper, String path) {
        Potion potion = BuiltInRegistries.POTION.getOptional(Distillation.id(path)).orElse(null);
        helper.assertTrue(potion != null, "potion not registered: distillation:" + path);
        return potion;
    }

    private static void assertSingleEffect(GameTestHelper helper, String path, int expectedTicks, int expectedAmplifier) {
        Potion potion = registered(helper, path);
        helper.assertTrue(potion.getEffects().size() == 1, path + " carries exactly one effect");
        MobEffectInstance effect = potion.getEffects().get(0);
        helper.assertTrue(effect.getDuration() == expectedTicks,
                path + " duration: expected " + expectedTicks + ", got " + effect.getDuration());
        helper.assertTrue(effect.getAmplifier() == expectedAmplifier,
                path + " amplifier: expected " + expectedAmplifier + ", got " + effect.getAmplifier());
    }

    private static String potionIdOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                .potion()
                .flatMap(Holder::unwrapKey)
                .map(key -> key.location().toString())
                .orElse("<none>");
    }

    private static void assertPotion(GameTestHelper helper, ItemStack stack, String expectedPotionId) {
        String actual = potionIdOf(stack);
        helper.assertTrue(actual.equals(expectedPotionId),
                "expected bottle to hold " + expectedPotionId + " but found " + actual);
    }

    private static void assertContains(GameTestHelper helper, RecipeGraph graph, String recipeId) {
        helper.assertTrue(graph.contains(ResourceLocation.parse(recipeId)),
                "recipe graph is missing " + recipeId);
    }
}
