package com.rfizzle.distillation.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.alchemy.Potion;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The murky draught's hint machinery ({@code design/SPEC.md} §1): which ingredient a failed
 * bottle's tooltip names, and which potion its flicker tastes of.
 *
 * <p>Selection is uniform over the candidate set and deterministic in a seed derived from stand
 * position and game time — bottles with the same input in one failed pass agree on their hint,
 * while different stands and different passes vary.
 */
public final class MurkyHints {

    private MurkyHints() {
    }

    /** One pass's shared hint seed: stand position and completion tick, mixed. */
    public static long seedFor(BlockPos pos, long gameTime) {
        return pos.asLong() ^ (gameTime * 0x9E3779B97F4A7C15L);
    }

    /**
     * Picks the hint uniformly from the candidates, deterministically per seed — a fresh
     * generator per call, so every bottle sharing a seed and candidate set picks the same entry
     * regardless of resolution order. Empty candidates yield the hintless draught.
     */
    public static <T> Optional<T> select(List<T> candidates, long seed) {
        return select(candidates, seed, ignored -> false);
    }

    /**
     * Picks the hint seeded-uniform, but from the {@code preferred} candidates when the set holds
     * any — the draught names a new liquid to brew (a potion conversion) over a mere container
     * swap whenever it can, falling back to the full set (container conversions alone) so a bottle
     * that only takes a container change still hints rather than going hintless. The preference is
     * a pure filter and the pick stays seed-deterministic, so bottles sharing a pass's seed and
     * candidate set still agree. Empty candidates yield the hintless draught.
     */
    public static <T> Optional<T> select(List<T> candidates, long seed, Predicate<T> preferred) {
        List<T> pool = candidates.stream().filter(preferred).toList();
        if (pool.isEmpty()) {
            pool = candidates;
        }
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        RandomSource random = RandomSource.create(seed);
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /**
     * The potion a draught's flicker applies: the output of the hinted conversion, re-resolved
     * against the live graph at drink time. A potion conversion from the input names its own
     * output; a hint that only matches container conversions (gunpowder, dragon's breath) keeps
     * the input potion — container conversions never change the liquid. Empty when the graph no
     * longer carries the hinted conversion (a mod or config change since bottling): the drink
     * degrades to nausea alone.
     */
    public static Optional<Holder<Potion>> flickerPotion(RecipeGraph graph, ResourceLocation inputPotionId,
                                                         ResourceLocation hintIngredientId) {
        boolean containerHint = false;
        for (RecipeGraph.Conversion conversion : graph.conversions()) {
            if (!BuiltInRegistries.ITEM.getKey(conversion.ingredient()).equals(hintIngredientId)) {
                continue;
            }
            if (conversion instanceof RecipeGraph.PotionConversion potion && potion.from().is(inputPotionId)) {
                return Optional.of(potion.to());
            }
            if (conversion instanceof RecipeGraph.ContainerConversion) {
                containerHint = true;
            }
        }
        if (!containerHint) {
            return Optional.empty();
        }
        // Optional<Holder.Reference<Potion>> widens to Optional<Holder<Potion>>.
        return BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, inputPotionId))
                .map(holder -> (Holder<Potion>) holder);
    }
}
