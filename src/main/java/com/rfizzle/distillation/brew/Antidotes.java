package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.recipe.RecipeIds;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The six targeted antidotes of {@code design/SPEC.md} §6 and the additive registration point the
 * Public API's {@code registerAntidote} appends to. Each antidote is an instant potion brewed on a
 * {@link Potions#THICK Thick} base from the affliction's own source, carrying the single shared
 * {@link CleanseMobEffect distillation:cleanse} effect at an amplifier that indexes back to the
 * effect it strips.
 *
 * <p>The ordered registry is the amplifier index: the six built-ins take indices 0–5, and every
 * {@code registerAntidote} call appends the next index. The antidote {@link Potion} bakes that index
 * as its cleanse instance's amplifier, so {@link #targetForIndex} resolves the target back at apply
 * time. Conversions are registered lazily inside the brewing-builder listener, so an antidote
 * registered late in mod init (a sibling's {@code onInitialize}) is still captured when the graph
 * builds.
 *
 * <p>Registrations are unconditional; {@code enableAntidotes=false} instead removes
 * {@link #ownedRecipeIds()} from the recipe graph, so existing antidote bottles keep working.
 */
public final class Antidotes {

    /** One antidote line: the effect it cures, its reagent, the registered potion, and its index. */
    public record Antidote(ResourceLocation effectId, Holder<MobEffect> target, Ingredient reagent,
                           List<Item> reagentItems, String path, int index, Holder<Potion> potion) {
    }

    /** A built-in antidote line's declaration (SPEC §6) — the table {@link #register()} registers from. */
    public record BuiltIn(Holder<MobEffect> target, Item reagent, String path) {
    }

    /** The six antidotes of SPEC §6, in index order — the source of truth the table test pins. */
    public static final List<BuiltIn> BUILTINS = List.of(
            new BuiltIn(MobEffects.POISON, Items.FERMENTED_SPIDER_EYE, "poison_antidote"),
            new BuiltIn(MobEffects.WITHER, Items.WITHER_ROSE, "wither_antidote"),
            new BuiltIn(MobEffects.DIG_SLOWDOWN, Items.PRISMARINE_CRYSTALS, "mining_fatigue_antidote"),
            new BuiltIn(MobEffects.BLINDNESS, Items.INK_SAC, "blindness_antidote"),
            new BuiltIn(MobEffects.DARKNESS, Items.ECHO_SHARD, "darkness_antidote"),
            new BuiltIn(MobEffects.LEVITATION, Items.POPPED_CHORUS_FRUIT, "levitation_antidote"));

    /** The cleanse instance carries a 1-tick duration (instant effects ignore it) and the index amplifier. */
    private static final int CLEANSE_COLOR = 0xC44DCC; // Potion Magenta (DESIGN §1); per-cure tint overrides it

    // Live registry, written only during mod init; every read after init sees the published snapshot.
    private static final List<Antidote> ANTIDOTES = new ArrayList<>();
    private static final Map<ResourceLocation, Antidote> BY_EFFECT = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Antidote> BY_POTION_ID = new LinkedHashMap<>();

    // Immutable snapshots, swapped by reference on each registration — the thread-safe read surface.
    private static volatile List<Antidote> published = List.of();
    private static volatile Set<ResourceLocation> publishedPotionIds = Set.of();
    private static volatile Map<ResourceLocation, Antidote> publishedByPotionId = Map.of();

    private static Holder<MobEffect> cleanse;
    private static boolean effectRegistered = false;
    private static boolean builtInsRegistered = false;

    private Antidotes() {
    }

    /** Registers the shared {@code distillation:cleanse} effect; must run before any antidote potion. */
    public static void registerEffect() {
        if (effectRegistered) {
            return;
        }
        effectRegistered = true;
        cleanse = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Distillation.id("cleanse"),
                new CleanseMobEffect(CLEANSE_COLOR));
    }

    /** The shared cleanse effect holder — exposed so a table test can assert the antidote wiring. */
    public static Holder<MobEffect> cleanse() {
        return cleanse;
    }

    /** Registers the six built-in antidote lines (§6). Idempotent; run once from mod init. */
    public static void register() {
        if (builtInsRegistered) {
            return;
        }
        builtInsRegistered = true;
        for (BuiltIn builtIn : BUILTINS) {
            add(builtIn.target(), builtIn.reagent(), builtIn.path());
        }
    }

    /**
     * Registers the Thick-base conversions through the vanilla brewing builder, read lazily so
     * antidotes registered after this call (a sibling's {@code registerAntidote}) still enter the
     * graph when {@code BUILD} fires.
     */
    public static void registerConversions() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            for (Antidote antidote : published) {
                builder.registerPotionRecipe(Potions.THICK, antidote.reagent(), antidote.potion());
            }
        });
    }

    /**
     * The sanctioned additive-registration point ({@code design/SPEC.md} §Public API): adds a
     * Thick-based antidote line for {@code effectId}, curing that effect with {@code reagent}.
     * Returns {@code false} and changes nothing when the effect already has an antidote or its id is
     * not a registered {@link MobEffect}. Callable during mod init only.
     */
    public static synchronized boolean registerAntidote(ResourceLocation effectId, Ingredient reagent) {
        if (!effectRegistered) {
            Distillation.LOGGER.warn("registerAntidote({}) before the cleanse effect is registered; ignored", effectId);
            return false;
        }
        if (BY_EFFECT.containsKey(effectId)) {
            return false;
        }
        Holder<MobEffect> target = BuiltInRegistries.MOB_EFFECT.getOptional(effectId)
                .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                .orElse(null);
        if (target == null) {
            Distillation.LOGGER.warn("registerAntidote({}) names no registered mob effect; ignored", effectId);
            return false;
        }
        List<Item> items = reagentItems(reagent);
        if (items.isEmpty()) {
            Distillation.LOGGER.warn("registerAntidote({}) has an empty reagent; ignored", effectId);
            return false;
        }
        add(effectId, target, reagent, items, pathFor(effectId));
        return true;
    }

    /** The target effect an antidote at this amplifier index cures, or {@code null} for an unknown index. */
    @Nullable
    public static Holder<MobEffect> targetForIndex(int index) {
        List<Antidote> local = published;
        return index >= 0 && index < local.size() ? local.get(index).target() : null;
    }

    /** The target effect this antidote potion cures — drives the per-cure tint and tooltip. */
    @Nullable
    public static Holder<MobEffect> targetForPotion(ResourceLocation potionId) {
        Antidote antidote = publishedByPotionId.get(potionId);
        return antidote == null ? null : antidote.target();
    }

    /** Whether a potion id is one of Distillation's antidotes — the tint/tooltip/model predicate. */
    public static boolean isAntidote(ResourceLocation potionId) {
        return publishedPotionIds.contains(potionId);
    }

    /** An immutable snapshot of the registered antidote lines, in index order. */
    public static List<Antidote> antidotes() {
        return published;
    }

    /**
     * The recipe ids of every antidote conversion — the set {@code enableAntidotes=false} removes
     * from the graph. Derived per call (the registry is tiny and only grows during init), matching
     * the graph builder's own id derivation exactly.
     */
    public static Set<ResourceLocation> ownedRecipeIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Antidote antidote : published) {
            for (Item item : antidote.reagentItems()) {
                RecipeIds.forPotionInput(item, Potions.THICK).ifPresent(ids::add);
            }
        }
        return Set.copyOf(ids);
    }

    private static void add(Holder<MobEffect> target, Item reagent, String path) {
        add(effectId(target), target, Ingredient.of(reagent), List.of(reagent), path);
    }

    private static void add(ResourceLocation effectId, Holder<MobEffect> target, Ingredient reagent,
                            List<Item> reagentItems, String path) {
        int index = ANTIDOTES.size();
        Holder<Potion> potion = Registry.registerForHolder(BuiltInRegistries.POTION, Distillation.id(path),
                new Potion(path, new MobEffectInstance(cleanse, 1, index)));
        Antidote antidote = new Antidote(effectId, target, reagent, reagentItems, path, index, potion);
        ResourceLocation potionId = Distillation.id(path);
        ANTIDOTES.add(antidote);
        BY_EFFECT.put(effectId, antidote);
        BY_POTION_ID.put(potionId, antidote);
        // Republish every read surface as an immutable snapshot, swapped by reference — so a render
        // or server-thread read after init always sees a fully-built, consistent view.
        published = List.copyOf(ANTIDOTES);
        publishedByPotionId = Map.copyOf(BY_POTION_ID);
        Set<ResourceLocation> potionIds = new LinkedHashSet<>(publishedPotionIds);
        potionIds.add(potionId);
        publishedPotionIds = Set.copyOf(potionIds);
    }

    private static ResourceLocation effectId(Holder<MobEffect> target) {
        return BuiltInRegistries.MOB_EFFECT.getKey(target.value());
    }

    private static List<Item> reagentItems(Ingredient reagent) {
        List<Item> items = new ArrayList<>();
        for (ItemStack stack : reagent.getItems()) {
            if (!stack.isEmpty() && !items.contains(stack.getItem())) {
                items.add(stack.getItem());
            }
        }
        return items;
    }

    /** {@code distillation:<path>_antidote} for a vanilla effect, namespaced for a modded one. */
    private static String pathFor(ResourceLocation effectId) {
        String base = ResourceLocation.DEFAULT_NAMESPACE.equals(effectId.getNamespace())
                ? effectId.getPath()
                : effectId.getNamespace() + "_" + effectId.getPath();
        return base + "_antidote";
    }
}
