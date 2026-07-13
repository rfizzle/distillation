package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.recipe.RecipeIds;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Distillation's brewing conversions ({@code design/SPEC.md} §2), registered through the vanilla
 * brewing builder via Fabric's {@code BUILD} event so they enter the §1 recipe graph with no
 * special casing — exactly like a third-party mod's registrations.
 *
 * <p>Registration is unconditional; {@code enableMissingBrews=false} instead removes
 * {@link #ownedRecipeIds()} from the graph, which the brew seam resolves everything through.
 * ({@code PotionBrewing} is built once per server and can't be rebuilt on a config flip, so the
 * feature is guarded at its entry seam per the vanilla-parity rule in {@code AGENTS.md}.)
 */
public final class DistillationBrews {

    private record Mix(Holder<Potion> from, Item ingredient, Holder<Potion> to) {
    }

    // Computed once from the mix table; published by reference swap (never mutated after).
    private static volatile Set<ResourceLocation> ownedRecipeIds;

    private DistillationBrews() {
    }

    public static void registerConversions() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            for (Mix mix : mixes()) {
                builder.registerPotionRecipe(mix.from(), Ingredient.of(mix.ingredient()), mix.to());
            }
        });
    }

    /**
     * The §2 conversion table. Built on demand — the potion holders exist once
     * {@link DistillationPotions#register()} has run.
     */
    private static List<Mix> mixes() {
        List<Mix> mixes = new ArrayList<>();
        // Resistance: Awkward + Shulker Shell.
        addLine(mixes, Potions.AWKWARD, Items.SHULKER_SHELL, "resistance", true);
        // Haste: Potion of Swiftness + Honey Bottle — deliberately two steps (§2).
        addLine(mixes, Potions.SWIFTNESS, Items.HONEY_BOTTLE, "haste", true);
        // Absorption: Awkward + Golden Apple.
        addLine(mixes, Potions.AWKWARD, Items.GOLDEN_APPLE, "absorption", true);
        // Luck: Awkward + Nautilus Shell. No meaningful level II — glowstone stays invalid.
        addLine(mixes, Potions.AWKWARD, Items.NAUTILUS_SHELL, "luck", false);
        // Glowing: Awkward + Glow Ink Sac. No level II.
        addLine(mixes, Potions.AWKWARD, Items.GLOW_INK_SAC, "glowing", false);
        // Levitation: Awkward + Chorus Fruit — raw fruit floats you, where the popped fruit brews
        // the levitation antidote. Short base, no level II — glowstone stays invalid.
        addLine(mixes, Potions.AWKWARD, Items.CHORUS_FRUIT, "levitation", false);
        // Health Boost: Awkward + Pumpkin Pie, an overworld delicacy. Mirrors Absorption's line.
        addLine(mixes, Potions.AWKWARD, Items.PUMPKIN_PIE, "health_boost", true);
        // Corruptions (§2): Haste → Mining Fatigue and Luck → Bad Luck, with the long mirror
        // vanilla's own corruptions carry (long_swiftness + eye → long_slowness).
        mixes.add(new Mix(DistillationPotions.potion("haste"), Items.FERMENTED_SPIDER_EYE,
                DistillationPotions.potion("mining_fatigue")));
        mixes.add(new Mix(DistillationPotions.potion("long_haste"), Items.FERMENTED_SPIDER_EYE,
                DistillationPotions.potion("long_mining_fatigue")));
        mixes.add(new Mix(DistillationPotions.potion("mining_fatigue"), Items.REDSTONE,
                DistillationPotions.potion("long_mining_fatigue")));
        mixes.add(new Mix(DistillationPotions.potion("luck"), Items.FERMENTED_SPIDER_EYE,
                DistillationPotions.potion("bad_luck")));
        mixes.add(new Mix(DistillationPotions.potion("long_luck"), Items.FERMENTED_SPIDER_EYE,
                DistillationPotions.potion("long_bad_luck")));
        mixes.add(new Mix(DistillationPotions.potion("bad_luck"), Items.REDSTONE,
                DistillationPotions.potion("long_bad_luck")));
        // The Mundane bottle's onward arrow (§2): Mundane + Fermented Spider Eye → Weakness,
        // alongside vanilla's untouched water-bottle route.
        mixes.add(new Mix(Potions.MUNDANE, Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS));
        return mixes;
    }

    private static void addLine(List<Mix> mixes, Holder<Potion> base, Item reagent, String path, boolean strong) {
        mixes.add(new Mix(base, reagent, DistillationPotions.potion(path)));
        mixes.add(new Mix(DistillationPotions.potion(path), Items.REDSTONE,
                DistillationPotions.potion("long_" + path)));
        if (strong) {
            mixes.add(new Mix(DistillationPotions.potion(path), Items.GLOWSTONE_DUST,
                    DistillationPotions.potion("strong_" + path)));
        }
    }

    /**
     * The recipe ids of every conversion this class registers — the set
     * {@code enableMissingBrews=false} removes from the graph.
     */
    public static Set<ResourceLocation> ownedRecipeIds() {
        Set<ResourceLocation> local = ownedRecipeIds;
        if (local == null) {
            Set<ResourceLocation> computed = new LinkedHashSet<>();
            for (Mix mix : mixes()) {
                RecipeIds.forPotionInput(mix.ingredient(), mix.from()).ifPresent(computed::add);
            }
            ownedRecipeIds = local = Set.copyOf(computed);
        }
        return local;
    }

    /**
     * Ingredients the stand consumes whole, with no crafting remainder back (§2: the Honey Bottle
     * leaves no empty glass bottle). Keyed on the ingredient item, so it covers the honey bottle
     * wherever it appears as a brewing ingredient; every other ingredient, dragon's breath
     * included, keeps exact vanilla remainder behavior.
     */
    public static boolean isConsumedWhole(ItemStack ingredient) {
        return ingredient.is(Items.HONEY_BOTTLE);
    }
}
