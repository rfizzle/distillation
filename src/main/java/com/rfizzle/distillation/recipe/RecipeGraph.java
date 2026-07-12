package com.rfizzle.distillation.recipe;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.item.DistillationItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The recipe graph of {@code design/SPEC.md} §1: the complete, immutable set of
 * {@code (input, ingredient) → output} brewing conversions, each carrying its stable
 * {@link RecipeIds recipe id}. Built from vanilla's {@link PotionBrewing} — which already holds
 * Distillation's own registrations and anything third-party mods registered through the same
 * registry — with each {@link Ingredient} expanded to per-item conversions (faithful in 1.21.1,
 * where ingredient matching is item-identity).
 *
 * <p>Resolution mirrors {@link PotionBrewing} exactly: {@link #resolve} is {@code mix} (container
 * conversions before potion conversions, first match wins, unmatched bottles pass through
 * unchanged), {@link #canBrew} is {@code hasMix}, {@link #isIngredient} is {@code isIngredient} —
 * so a graph built unfiltered brews byte-identically to vanilla.
 */
public final class RecipeGraph {

    /** One graph edge: brewing {@link #ingredient()} onto the input produces the output. */
    public sealed interface Conversion permits PotionConversion, ContainerConversion {
        ResourceLocation id();

        Item ingredient();
    }

    /** Changes the bottle's potion, keeping its item — the {@code Mix<Potion>} shape. */
    public record PotionConversion(ResourceLocation id, Item ingredient, Holder<Potion> from,
                                   Holder<Potion> to) implements Conversion {
    }

    /** Changes the bottle's item, keeping its potion — the {@code Mix<Item>} shape (gunpowder, dragon's breath). */
    public record ContainerConversion(ResourceLocation id, Item ingredient, Item from,
                                      Item to) implements Conversion {
    }

    private final Set<Item> containerItems;
    private final Map<Item, List<ContainerConversion>> containerByIngredient;
    private final Map<Item, List<PotionConversion>> potionByIngredient;
    private final List<Conversion> conversions;
    private final Map<ResourceLocation, Conversion> byId;
    private final Set<ResourceLocation> ids;

    private RecipeGraph(Set<Item> containerItems,
                        Map<Item, List<ContainerConversion>> containerByIngredient,
                        Map<Item, List<PotionConversion>> potionByIngredient,
                        List<Conversion> conversions,
                        Map<ResourceLocation, Conversion> byId,
                        Set<ResourceLocation> ids) {
        this.containerItems = containerItems;
        this.containerByIngredient = containerByIngredient;
        this.potionByIngredient = potionByIngredient;
        this.conversions = conversions;
        this.byId = byId;
        this.ids = ids;
    }

    /**
     * Builds the graph from a live brewing registry, dropping any conversion whose recipe id is in
     * {@code excludedIds} (how {@code enableMissingBrews=false} removes Distillation's lines while
     * their potions stay registered and existing bottles keep working).
     */
    public static RecipeGraph fromBrewing(PotionBrewing brewing, Set<ResourceLocation> excludedIds) {
        Builder builder = new Builder(excludedIds);
        for (Ingredient container : brewing.containers) {
            for (ItemStack stack : container.getItems()) {
                builder.addContainer(stack.getItem());
            }
        }
        for (PotionBrewing.Mix<Item> mix : brewing.containerMixes) {
            builder.addContainerConversion(mix.from().value(), mix.ingredient(), mix.to().value());
        }
        for (PotionBrewing.Mix<Potion> mix : brewing.potionMixes) {
            builder.addPotionConversion(mix.from(), mix.ingredient(), mix.to());
        }
        return builder.build();
    }

    /** Mirror of {@code PotionBrewing.isIngredient}: the stack serves at least one conversion. */
    public boolean isIngredient(ItemStack stack) {
        Item item = stack.getItem();
        return containerByIngredient.containsKey(item) || potionByIngredient.containsKey(item);
    }

    /**
     * Mirror of {@code PotionBrewing.hasMix(bottle, ingredient)}: the bottle is a recognized
     * container and some conversion applies. Gates cycle start ({@code isBrewable}).
     */
    public boolean canBrew(ItemStack bottle, ItemStack ingredient) {
        if (!containerItems.contains(bottle.getItem())) {
            return false;
        }
        Item ingredientItem = ingredient.getItem();
        for (ContainerConversion conversion : containerByIngredient.getOrDefault(ingredientItem, List.of())) {
            if (bottle.is(conversion.from())) {
                return true;
            }
        }
        Optional<Holder<Potion>> potion = potionOf(bottle);
        if (potion.isEmpty()) {
            return false;
        }
        for (PotionConversion conversion : potionByIngredient.getOrDefault(ingredientItem, List.of())) {
            if (conversion.from().is(potion.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mirror of {@code PotionBrewing.mix(ingredient, bottle)}: resolves one bottle through the
     * graph — container conversions first, then potion conversions, first match wins; a bottle
     * with no matching conversion (or no potion contents) passes through unchanged, same reference.
     */
    public ItemStack resolve(ItemStack ingredient, ItemStack bottle) {
        Conversion conversion = matchConversion(ingredient, bottle);
        return conversion == null ? bottle : outputOf(conversion, bottle);
    }

    /**
     * The conversion {@link #resolve} would apply to this {@code (ingredient, bottle)} pair —
     * container conversions first, then potion conversions, first match wins — or {@code null}
     * when the bottle passes through unchanged. Exposed so the brew seam can record which recipe
     * id produced each bottle (discovery provenance) alongside the resolution itself.
     */
    @Nullable
    public Conversion matchConversion(ItemStack ingredient, ItemStack bottle) {
        if (bottle.isEmpty()) {
            return null;
        }
        Optional<Holder<Potion>> potion = potionOf(bottle);
        if (potion.isEmpty()) {
            return null;
        }
        Item ingredientItem = ingredient.getItem();
        for (ContainerConversion conversion : containerByIngredient.getOrDefault(ingredientItem, List.of())) {
            if (bottle.is(conversion.from())) {
                return conversion;
            }
        }
        for (PotionConversion conversion : potionByIngredient.getOrDefault(ingredientItem, List.of())) {
            if (conversion.from().is(potion.get())) {
                return conversion;
            }
        }
        return null;
    }

    /**
     * The output stack a conversion produces from this bottle. Callers must pass a bottle the
     * conversion {@linkplain #matchConversion matched} — a container conversion keeps the bottle's
     * potion, so the bottle must carry one.
     */
    public ItemStack outputOf(Conversion conversion, ItemStack bottle) {
        if (conversion instanceof ContainerConversion container) {
            return PotionContents.createItemStack(container.to(), potionOf(bottle).orElseThrow(
                    () -> new IllegalArgumentException("container conversion applied to a potionless bottle")));
        }
        PotionConversion potion = (PotionConversion) conversion;
        return PotionContents.createItemStack(bottle.getItem(), potion.to());
    }

    /**
     * A bottle the stand can work on at all: a recognized brewing container carrying a potion.
     * This is the murky-era cycle gate ({@code design/SPEC.md} §1 — with murky draughts on, a
     * receptive bottle starts a cycle whether or not its pair is valid) and is deliberately a
     * stable predicate on the stack's current contents, because vanilla re-checks
     * {@code isBrewable} every tick of a running cycle. A Murky Draught is never receptive: it is
     * not a brewing container and carries no potion contents.
     */
    public boolean isReceptive(ItemStack bottle) {
        return !bottle.isEmpty() && containerItems.contains(bottle.getItem()) && potionOf(bottle).isPresent();
    }

    /**
     * Every conversion some ingredient would apply to this exact bottle (item and potion) — the
     * murky hint-candidate set: each entry's ingredient is one that genuinely "would have taken."
     * Empty for a bottle nothing brews onward from (the hintless draught).
     */
    public List<Conversion> conversionsFor(ItemStack bottle) {
        if (bottle.isEmpty()) {
            return List.of();
        }
        Optional<Holder<Potion>> potion = potionOf(bottle);
        if (potion.isEmpty()) {
            return List.of();
        }
        List<Conversion> matches = new ArrayList<>();
        for (Conversion conversion : conversions) {
            if (conversion instanceof ContainerConversion container && bottle.is(container.from())) {
                matches.add(container);
            } else if (conversion instanceof PotionConversion potionConversion
                    && potionConversion.from().is(potion.get())) {
                matches.add(potionConversion);
            }
        }
        return List.copyOf(matches);
    }

    /** The conversion carrying this recipe id, if the current graph has one — an O(1) lookup. */
    public Optional<Conversion> conversionById(ResourceLocation recipeId) {
        return Optional.ofNullable(byId.get(recipeId));
    }

    /** Every conversion, in registration order. */
    public List<Conversion> conversions() {
        return conversions;
    }

    /** Every stable recipe id in the graph. */
    public Set<ResourceLocation> ids() {
        return ids;
    }

    public boolean contains(ResourceLocation recipeId) {
        return ids.contains(recipeId);
    }

    private static Optional<Holder<Potion>> potionOf(ItemStack bottle) {
        // A half draught holds a potion to drink but is never a receptive brewing bottle (SPEC §4):
        // reporting it potionless here makes it inert to every graph path — no cycle starts over it,
        // it never brews onward, and it never murks (the stand rejects it, no topping up).
        if (bottle.has(DistillationItems.DRAUGHT)) {
            return Optional.empty();
        }
        return bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
    }

    /**
     * Assembles a graph from individual conversions — the seam unit tests build synthetic
     * registries through, and the shape {@link #fromBrewing} feeds vanilla's lists into.
     */
    public static final class Builder {
        private final Set<ResourceLocation> excludedIds;
        private final Set<Item> containerItems = new LinkedHashSet<>();
        private final List<Conversion> conversions = new ArrayList<>();

        public Builder(Set<ResourceLocation> excludedIds) {
            this.excludedIds = excludedIds;
        }

        public Builder addContainer(Item item) {
            containerItems.add(item);
            return this;
        }

        public Builder addPotionConversion(Holder<Potion> from, Ingredient ingredient, Holder<Potion> to) {
            if (from.unwrapKey().isEmpty()) {
                Distillation.LOGGER.warn("Skipping graph entry with keyless input potion holder (ingredient {})",
                        ingredient);
                return this;
            }
            for (Item item : expand(ingredient)) {
                ResourceLocation id = RecipeIds.forPotionInput(item, from).orElseThrow();
                if (!excludedIds.contains(id)) {
                    conversions.add(new PotionConversion(id, item, from, to));
                }
            }
            return this;
        }

        public Builder addContainerConversion(Item from, Ingredient ingredient, Item to) {
            for (Item item : expand(ingredient)) {
                ResourceLocation id = RecipeIds.derive(itemId(item), itemId(from));
                if (!excludedIds.contains(id)) {
                    conversions.add(new ContainerConversion(id, item, from, to));
                }
            }
            return this;
        }

        public RecipeGraph build() {
            Map<Item, List<ContainerConversion>> containerByIngredient = new LinkedHashMap<>();
            Map<Item, List<PotionConversion>> potionByIngredient = new LinkedHashMap<>();
            Map<ResourceLocation, Conversion> byId = new LinkedHashMap<>();
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (Conversion conversion : conversions) {
                ids.add(conversion.id());
                // First-wins, matching the resolution order resolve/matchConversion walk.
                byId.putIfAbsent(conversion.id(), conversion);
                if (conversion instanceof ContainerConversion container) {
                    containerByIngredient.computeIfAbsent(container.ingredient(), item -> new ArrayList<>())
                            .add(container);
                } else if (conversion instanceof PotionConversion potion) {
                    potionByIngredient.computeIfAbsent(potion.ingredient(), item -> new ArrayList<>())
                            .add(potion);
                }
            }
            containerByIngredient.replaceAll((item, list) -> List.copyOf(list));
            potionByIngredient.replaceAll((item, list) -> List.copyOf(list));
            return new RecipeGraph(
                    Set.copyOf(containerItems),
                    Collections.unmodifiableMap(containerByIngredient),
                    Collections.unmodifiableMap(potionByIngredient),
                    List.copyOf(conversions),
                    Collections.unmodifiableMap(byId),
                    Collections.unmodifiableSet(ids));
        }

        private static List<Item> expand(Ingredient ingredient) {
            // 1.21.1 Ingredient matching is item-identity, so per-item expansion is faithful.
            List<Item> items = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (!items.contains(stack.getItem())) {
                    items.add(stack.getItem());
                }
            }
            return items;
        }

        private static ResourceLocation itemId(Item item) {
            return BuiltInRegistries.ITEM.getKey(item);
        }
    }
}
