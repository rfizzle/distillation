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
        // Corruptions (§2): fermented spider eye inverts an effect into its opposite, completing the
        // set vanilla ships (Swiftness/Leaping → Slowness, Night Vision → Invisibility, Healing/Poison
        // → Harming, Water → Weakness). Extended inputs invert to the extended opposite; each output
        // already carries its own redstone route (vanilla's, or the §2 line's above), so the only
        // redstone edges added here are for Mining Fatigue and Bad Luck, which have no vanilla base.
        corrupt(mixes, DistillationPotions.potion("haste"), DistillationPotions.potion("mining_fatigue"));
        corrupt(mixes, DistillationPotions.potion("long_haste"), DistillationPotions.potion("long_mining_fatigue"));
        mixes.add(new Mix(DistillationPotions.potion("mining_fatigue"), Items.REDSTONE,
                DistillationPotions.potion("long_mining_fatigue")));
        corrupt(mixes, DistillationPotions.potion("luck"), DistillationPotions.potion("bad_luck"));
        corrupt(mixes, DistillationPotions.potion("long_luck"), DistillationPotions.potion("long_bad_luck"));
        mixes.add(new Mix(DistillationPotions.potion("bad_luck"), Items.REDSTONE,
                DistillationPotions.potion("long_bad_luck")));
        // Strength → Weakness — the canonical inversion vanilla left unwired. No strong_weakness
        // exists, so strong_strength stays an invalid pair, as vanilla leaves strong_swiftness.
        corrupt(mixes, Potions.STRENGTH, Potions.WEAKNESS);
        corrupt(mixes, Potions.LONG_STRENGTH, Potions.LONG_WEAKNESS);
        // Regeneration → Poison — heal-over-time inverts to damage-over-time.
        corrupt(mixes, Potions.REGENERATION, Potions.POISON);
        corrupt(mixes, Potions.LONG_REGENERATION, Potions.LONG_POISON);
        corrupt(mixes, Potions.STRONG_REGENERATION, Potions.STRONG_POISON);
        // Glowing → Invisibility — reveal inverts to conceal, mirroring vanilla's Night Vision route.
        corrupt(mixes, DistillationPotions.potion("glowing"), Potions.INVISIBILITY);
        corrupt(mixes, DistillationPotions.potion("long_glowing"), Potions.LONG_INVISIBILITY);
        // Slow Falling → Levitation — a gentle descent inverts to a forced rise.
        corrupt(mixes, Potions.SLOW_FALLING, DistillationPotions.potion("levitation"));
        corrupt(mixes, Potions.LONG_SLOW_FALLING, DistillationPotions.potion("long_levitation"));
        // The Mundane bottle's onward arrow (§2): Mundane + Fermented Spider Eye → Weakness,
        // alongside vanilla's untouched water-bottle route.
        corrupt(mixes, Potions.MUNDANE, Potions.WEAKNESS);
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

    /** A fermented-spider-eye conversion edge (§2) — the corruptions and the Mundane onward arrow. */
    private static void corrupt(List<Mix> mixes, Holder<Potion> from, Holder<Potion> to) {
        mixes.add(new Mix(from, Items.FERMENTED_SPIDER_EYE, to));
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
