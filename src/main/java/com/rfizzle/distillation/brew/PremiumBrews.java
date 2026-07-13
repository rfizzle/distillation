package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.recipe.RecipeIds;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Concentrated &amp; premium brews ({@code design/SPEC.md} §5): redstone and glowstone stop being
 * mutually exclusive, at a doubled reagent cost. Only lines with a strong (level II+) form are
 * eligible.
 *
 * <p>Each line is a four-state brewing automaton, registered as four parallel potions and wired
 * through the vanilla brewing builder (so it enters the §1 recipe graph like any other conversion):
 * <pre>
 *   base ─(own reagent)→ concentrated ─(redstone)→ concentrated_long ─(glowstone)┐
 *                            │                                                    ├→ premium
 *                            └────────(glowstone)→ concentrated_strong ─(redstone)┘
 * </pre>
 * The two intermediates are distinct so both dust <em>orders</em> converge on {@code premium} while
 * a single dust never yields it. Concentrated variants copy the base/long/strong potion's effects
 * verbatim ("identical stats" by construction); {@code premium} copies the strong variant's effects
 * (correct amplifier, including Turtle Master's two) at the §5.3-enumerated duration.
 *
 * <p>Registration is unconditional; {@code enablePremiumBrews=false} instead removes
 * {@link #ownedRecipeIds()} from the graph, so existing Concentrated and premium bottles keep
 * working (their deeper color and Concentrated tag are identity-bound, not toggle-bound).
 */
public final class PremiumBrews {

    /**
     * One eligible line: its {@code base}/{@code longVariant}/{@code strongVariant} potion holders
     * (vanilla or §2), the reagent that concentrates the base, and the premium effect duration in
     * ticks (SPEC §5.3 — the enumerated table, authoritative over the {@code long ÷ 2} guide for
     * Regeneration, Poison, and Turtle Master). {@code name} is the shared display name, so all four
     * variants ride the base line's {@code item.minecraft.potion.effect.<name>} key.
     */
    public record Line(String path, String name, Holder<Potion> base, Holder<Potion> longVariant,
                       Holder<Potion> strongVariant, Item reagent, int premiumTicks) {
    }

    /**
     * The reagent that concentrates each eligible line's base potion — its "own effect reagent",
     * the ingredient that brews the base in the first place. Pure data (single source of truth for
     * both {@link #lines()} and the table test), keyed by line path.
     */
    public static final Map<String, Item> REAGENTS = Map.ofEntries(
            Map.entry("strength", Items.BLAZE_POWDER),
            Map.entry("swiftness", Items.SUGAR),
            Map.entry("leaping", Items.RABBIT_FOOT),
            Map.entry("regeneration", Items.GHAST_TEAR),
            Map.entry("poison", Items.SPIDER_EYE),
            // Slowness has no direct base reagent in vanilla; Fermented Spider Eye is the only
            // ingredient that produces it, and Slowness + FSE is an empty vanilla slot (§5.3).
            Map.entry("slowness", Items.FERMENTED_SPIDER_EYE),
            Map.entry("turtle_master", Items.TURTLE_HELMET),
            Map.entry("resistance", Items.SHULKER_SHELL),
            Map.entry("absorption", Items.GOLDEN_APPLE),
            Map.entry("haste", Items.HONEY_BOTTLE),
            Map.entry("health_boost", Items.PUMPKIN_PIE));

    /**
     * The premium effect duration in ticks per line — SPEC §5.3's enumerated table, authoritative
     * over the {@code long ÷ 2} guide (which it matches except for Regeneration, Poison, and Turtle
     * Master). The premium amplifier is the strong variant's, derived from that holder.
     */
    public static final Map<String, Integer> PREMIUM_TICKS = Map.ofEntries(
            Map.entry("strength", 4800),
            Map.entry("swiftness", 4800),
            Map.entry("leaping", 4800),
            Map.entry("regeneration", 1800),
            Map.entry("poison", 1800),
            Map.entry("slowness", 2400),
            Map.entry("turtle_master", 2400),
            Map.entry("resistance", 4800),
            Map.entry("absorption", 4800),
            Map.entry("haste", 12000),
            Map.entry("health_boost", 4800));

    /** The eligible lines whose base is a §2 registration (their premium follows missing-brews too). */
    private static final Set<String> DISTILLATION_BACKED = Set.of("resistance", "absorption", "haste", "health_boost");

    private static final Map<String, Holder<Potion>> REGISTERED = new LinkedHashMap<>();
    private static volatile Set<ResourceLocation> familyIds = Set.of();
    // Computed once from the line table; published by reference swap (never mutated after).
    private static volatile Set<ResourceLocation> ownedRecipeIds;
    private static volatile Set<ResourceLocation> distillationBackedIds;
    private static boolean registered = false;

    private PremiumBrews() {
    }

    /**
     * The §5 line table. Built on demand — the §2 potion holders exist once
     * {@link DistillationPotions#register()} has run, and the vanilla holders once bootstrap has.
     */
    public static List<Line> lines() {
        return List.of(
                vanilla("strength", Potions.STRENGTH, Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH),
                vanilla("swiftness", Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS),
                vanilla("leaping", Potions.LEAPING, Potions.LONG_LEAPING, Potions.STRONG_LEAPING),
                vanilla("regeneration", Potions.REGENERATION, Potions.LONG_REGENERATION, Potions.STRONG_REGENERATION),
                vanilla("poison", Potions.POISON, Potions.LONG_POISON, Potions.STRONG_POISON),
                vanilla("slowness", Potions.SLOWNESS, Potions.LONG_SLOWNESS, Potions.STRONG_SLOWNESS),
                vanilla("turtle_master", Potions.TURTLE_MASTER, Potions.LONG_TURTLE_MASTER, Potions.STRONG_TURTLE_MASTER),
                // §2 lines with a strong form (Luck, Glowing, and Levitation have none, so they are ineligible).
                distillation("resistance"),
                distillation("absorption"),
                distillation("haste"),
                distillation("health_boost"));
    }

    /** A line whose base/long/strong variants are vanilla potions; name equals the path. */
    private static Line vanilla(String path, Holder<Potion> base, Holder<Potion> longVariant,
                                Holder<Potion> strongVariant) {
        return new Line(path, path, base, longVariant, strongVariant, REAGENTS.get(path), PREMIUM_TICKS.get(path));
    }

    /** A line whose base/long/strong variants are Distillation's own §2 registrations. */
    private static Line distillation(String path) {
        return new Line(path, path, DistillationPotions.potion(path), DistillationPotions.potion("long_" + path),
                DistillationPotions.potion("strong_" + path), REAGENTS.get(path), PREMIUM_TICKS.get(path));
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Set<ResourceLocation> family = new LinkedHashSet<>();
        for (Line line : lines()) {
            family.add(registerPotion("concentrated_" + line.path(),
                    new Potion(line.name(), copyEffects(line.base()))));
            family.add(registerPotion("concentrated_long_" + line.path(),
                    new Potion(line.name(), copyEffects(line.longVariant()))));
            family.add(registerPotion("concentrated_strong_" + line.path(),
                    new Potion(line.name(), copyEffects(line.strongVariant()))));
            family.add(registerPotion("premium_" + line.path(),
                    new Potion(line.name(), atDuration(line.strongVariant(), line.premiumTicks()))));
        }
        familyIds = Set.copyOf(family);
    }

    public static void registerConversions() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            for (Line line : lines()) {
                String p = line.path();
                builder.registerPotionRecipe(line.base(), Ingredient.of(line.reagent()), potion("concentrated_" + p));
                builder.registerPotionRecipe(potion("concentrated_" + p), Ingredient.of(Items.REDSTONE),
                        potion("concentrated_long_" + p));
                builder.registerPotionRecipe(potion("concentrated_" + p), Ingredient.of(Items.GLOWSTONE_DUST),
                        potion("concentrated_strong_" + p));
                builder.registerPotionRecipe(potion("concentrated_long_" + p), Ingredient.of(Items.GLOWSTONE_DUST),
                        potion("premium_" + p));
                builder.registerPotionRecipe(potion("concentrated_strong_" + p), Ingredient.of(Items.REDSTONE),
                        potion("premium_" + p));
            }
        });
    }

    /** The registered holder for a path from {@link #lines()} (e.g. {@code "premium_strength"}). */
    public static Holder<Potion> potion(String path) {
        return Objects.requireNonNull(REGISTERED.get(path), () -> "potion not registered: " + path);
    }

    /**
     * Whether a potion id belongs to the concentrated/premium family — the predicate the deeper
     * liquid color and "Concentrated" tooltip line key off. Identity-bound, so it holds regardless
     * of {@code enablePremiumBrews} (existing bottles keep their look).
     */
    public static boolean isConcentrated(ResourceLocation potionId) {
        return familyIds.contains(potionId);
    }

    /**
     * Whether a potion id is a finished premium output — extended and amplified ({@code design/SPEC.md}
     * §5), the {@code premium_*} member of the concentrated family. The advancement observer keys The
     * Good Stuff off this; concentrated intermediates return {@code false}.
     */
    public static boolean isPremium(ResourceLocation potionId) {
        return potionId.getPath().startsWith("premium_") && isConcentrated(potionId);
    }

    /**
     * The recipe ids of every conversion this class registers — the set
     * {@code enablePremiumBrews=false} removes from the graph.
     */
    public static Set<ResourceLocation> ownedRecipeIds() {
        Set<ResourceLocation> local = ownedRecipeIds;
        if (local == null) {
            Set<ResourceLocation> computed = new LinkedHashSet<>();
            for (Line line : lines()) {
                computed.addAll(lineIds(line));
            }
            ownedRecipeIds = local = Set.copyOf(computed);
        }
        return local;
    }

    /**
     * The subset of {@link #ownedRecipeIds()} whose base is a §2 line (Resistance, Absorption,
     * Haste). These leave the graph when {@code enableMissingBrews} is off as well — concentrating a
     * base you can no longer brew is a dangling conversion, and it would otherwise keep the §2
     * reagent (e.g. Shulker Shell) a graph ingredient after its line was disabled.
     */
    public static Set<ResourceLocation> distillationBackedRecipeIds() {
        Set<ResourceLocation> local = distillationBackedIds;
        if (local == null) {
            Set<ResourceLocation> computed = new LinkedHashSet<>();
            for (Line line : lines()) {
                if (DISTILLATION_BACKED.contains(line.path())) {
                    computed.addAll(lineIds(line));
                }
            }
            distillationBackedIds = local = Set.copyOf(computed);
        }
        return local;
    }

    /** The five recipe ids one line contributes: concentration, the two dusts, and the two completions. */
    private static List<ResourceLocation> lineIds(Line line) {
        List<ResourceLocation> ids = new ArrayList<>();
        String p = line.path();
        addId(ids, line.reagent(), line.base());
        addId(ids, Items.REDSTONE, potion("concentrated_" + p));
        addId(ids, Items.GLOWSTONE_DUST, potion("concentrated_" + p));
        addId(ids, Items.GLOWSTONE_DUST, potion("concentrated_long_" + p));
        addId(ids, Items.REDSTONE, potion("concentrated_strong_" + p));
        return ids;
    }

    private static void addId(List<ResourceLocation> ids, Item ingredient, Holder<Potion> from) {
        RecipeIds.forPotionInput(ingredient, from).ifPresent(ids::add);
    }

    private static ResourceLocation registerPotion(String path, Potion potion) {
        ResourceLocation id = Distillation.id(path);
        REGISTERED.put(path, Registry.registerForHolder(BuiltInRegistries.POTION, id, potion));
        return id;
    }

    /** A fresh copy of a potion's effects, verbatim — "identical stats" for a concentrated variant. */
    private static MobEffectInstance[] copyEffects(Holder<Potion> holder) {
        List<MobEffectInstance> effects = holder.value().getEffects();
        MobEffectInstance[] out = new MobEffectInstance[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            out[i] = at(effects.get(i), effects.get(i).getDuration());
        }
        return out;
    }

    /** A copy of a potion's effects at a fixed duration — the premium's amplifiers, its own timer. */
    private static MobEffectInstance[] atDuration(Holder<Potion> holder, int ticks) {
        List<MobEffectInstance> effects = holder.value().getEffects();
        MobEffectInstance[] out = new MobEffectInstance[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            out[i] = at(effects.get(i), ticks);
        }
        return out;
    }

    private static MobEffectInstance at(MobEffectInstance base, int ticks) {
        return new MobEffectInstance(base.getEffect(), ticks, base.getAmplifier(),
                base.isAmbient(), base.isVisible(), base.showIcon());
    }
}
