// Tier: 1 (pure JUnit — seed math and selection only; RandomSource is a POJO)
package com.rfizzle.distillation.recipe;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    void seedVariesByStandAndByTime() {
        long base = MurkyHints.seedFor(new BlockPos(10, 64, -3), 12000L);
        assertNotEquals(base, MurkyHints.seedFor(new BlockPos(11, 64, -3), 12000L),
                "different stands seed differently");
        assertNotEquals(base, MurkyHints.seedFor(new BlockPos(10, 64, -3), 12400L),
                "different passes seed differently");
    }
}
