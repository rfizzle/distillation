// Tier: 1 (pure JUnit — seed math and selection only; RandomSource is a POJO)
package com.rfizzle.distillation.recipe;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeded hint pick ({@code design/SPEC.md} §1): deterministic per seed — so every bottle of
 * one failed pass agrees — always drawn from the candidate set, hintless on an empty set, and
 * varying across stands and passes.
 */
class MurkyHintsTest {

    private static final List<String> CANDIDATES = List.of("wart", "gunpowder", "shell", "honey", "eye");

    // Models the potion-first rule of the seam: "gunpowder" stands in for a container conversion,
    // everything else for a potion conversion, so "preferred" is every candidate but gunpowder.
    private static final Predicate<String> PREFERRED = candidate -> !candidate.equals("gunpowder");
    private static final Set<String> PREFERRED_SET = Set.of("wart", "shell", "honey", "eye");

    @Test
    void sameSeedAndCandidatesAlwaysAgree() {
        long seed = MurkyHints.seedFor(new BlockPos(10, 64, -3), 12000L);
        Optional<String> first = MurkyHints.select(CANDIDATES, seed);
        for (int bottle = 0; bottle < 3; bottle++) {
            assertEquals(first, MurkyHints.select(CANDIDATES, seed),
                    "bottles sharing a pass's seed must carry the same hint");
        }
    }

    @Test
    void pickIsAlwaysACandidate() {
        for (long seed = 0; seed < 200; seed++) {
            Optional<String> pick = MurkyHints.select(CANDIDATES, seed);
            assertTrue(pick.isPresent() && CANDIDATES.contains(pick.get()),
                    "the hint must name an ingredient that would have taken");
        }
    }

    @Test
    void emptyCandidatesYieldTheHintlessDraught() {
        assertEquals(Optional.empty(), MurkyHints.select(List.of(), 42L));
    }

    @Test
    void selectionCoversTheCandidateSetAcrossSeeds() {
        Set<String> picked = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            MurkyHints.select(CANDIDATES, seed).ifPresent(picked::add);
        }
        assertEquals(Set.copyOf(CANDIDATES), picked,
                "uniform selection reaches every candidate across seeds");
    }

    @Test
    void preferredSubsetIsChosenWheneverItHasAny() {
        for (long seed = 0; seed < 200; seed++) {
            Optional<String> pick = MurkyHints.select(CANDIDATES, seed, PREFERRED);
            assertTrue(pick.isPresent() && PREFERRED_SET.contains(pick.get()),
                    "a set holding preferred candidates never names a non-preferred one (seed " + seed + ")");
        }
    }

    @Test
    void fallsBackToTheFullSetWhenNoneArePreferred() {
        List<String> containerOnly = List.of("gunpowder");
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(Optional.of("gunpowder"), MurkyHints.select(containerOnly, seed, PREFERRED),
                    "with no preferred candidate, the sole container hint is still picked");
        }
    }

    @Test
    void preferredSelectionStaysSeedDeterministic() {
        long seed = MurkyHints.seedFor(new BlockPos(4, 70, 8), 8000L);
        Optional<String> first = MurkyHints.select(CANDIDATES, seed, PREFERRED);
        for (int bottle = 0; bottle < 3; bottle++) {
            assertEquals(first, MurkyHints.select(CANDIDATES, seed, PREFERRED),
                    "bottles sharing a pass's seed and candidate set agree under the preference too");
        }
    }

    @Test
    void preferredSelectionCoversEveryPreferredCandidate() {
        // Draw seeds the way a real pass does — seedFor over varied stand positions — so the pick
        // sees well-scrambled seeds rather than the raw 0,1,2… a legacy LCG distributes poorly for
        // a power-of-two-sized pool.
        Set<String> picked = new HashSet<>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                long seed = MurkyHints.seedFor(new BlockPos(x, 64, z), 1000L);
                MurkyHints.select(CANDIDATES, seed, PREFERRED).ifPresent(picked::add);
            }
        }
        assertEquals(PREFERRED_SET, picked,
                "uniform selection reaches every preferred candidate and no other");
    }

    @Test
    void emptyCandidatesWithAPredicateStillYieldTheHintlessDraught() {
        assertEquals(Optional.empty(), MurkyHints.select(List.of(), 42L, PREFERRED));
    }

    @Test
    void seedVariesByStandAndByTime() {
        long base = MurkyHints.seedFor(new BlockPos(10, 64, -3), 12000L);
        assertNotEquals(base, MurkyHints.seedFor(new BlockPos(11, 64, -3), 12000L),
                "different stands seed differently");
        assertNotEquals(base, MurkyHints.seedFor(new BlockPos(10, 64, -3), 12400L),
                "different passes seed differently");
    }
}
