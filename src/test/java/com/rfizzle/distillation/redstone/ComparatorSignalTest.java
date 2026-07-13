package com.rfizzle.distillation.redstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tier-1 coverage of the pure two-band comparator scale ({@code design/SPEC.md} §9). */
class ComparatorSignalTest {

    @Test
    void idleWithNoBottlesIsZero() {
        assertEquals(0, ComparatorSignal.of(false, 0));
    }

    @Test
    void brewingWithNoBottlesIsZero() {
        // Can't happen in-world (a cycle needs a bottle), but the scale must still stay in range.
        assertEquals(0, ComparatorSignal.of(true, 0));
    }

    @Test
    void brewingReportsBottleCountInTheWorkingBand() {
        assertEquals(1, ComparatorSignal.of(true, 1));
        assertEquals(3, ComparatorSignal.of(true, 3));
        assertEquals(6, ComparatorSignal.of(true, 6)); // a full rigged batch mid-cycle
    }

    @Test
    void idleWithBottlesReportsCountPlusSevenInTheDoneBand() {
        assertEquals(8, ComparatorSignal.of(false, 1));
        assertEquals(10, ComparatorSignal.of(false, 3));
        assertEquals(13, ComparatorSignal.of(false, 6));
    }

    @Test
    void theBandsNeverOverlapAndDoneIsASingleThreshold() {
        // Working tops out below DONE_BASE; done starts at DONE_BASE. 7 is the unused separating gap.
        assertEquals(ComparatorSignal.WORKING_MAX, ComparatorSignal.of(true, 6));
        assertTrue(ComparatorSignal.of(true, ComparatorSignal.WORKING_MAX) < ComparatorSignal.DONE_BASE);
        assertEquals(ComparatorSignal.DONE_BASE, ComparatorSignal.of(false, 1));
    }

    @Test
    void countIsClampedIntoRange() {
        // An out-of-range count (defensive; the container only has six countable slots) never escapes.
        assertEquals(ComparatorSignal.WORKING_MAX, ComparatorSignal.of(true, 9));
        assertEquals(13, ComparatorSignal.of(false, 9));
        assertEquals(0, ComparatorSignal.of(true, -4));
        assertEquals(0, ComparatorSignal.of(false, -4));
    }
}
