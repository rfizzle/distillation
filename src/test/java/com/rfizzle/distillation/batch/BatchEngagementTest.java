// Tier: 1 (pure JUnit — BatchBrew.engages is arithmetic over primitives, no Minecraft runtime)
package com.rfizzle.distillation.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch-engagement gate ({@code design/SPEC.md} §3): a rigged pass scales to six bottles only
 * when the ingredient count covers the batch cost, fuel covers the batch fuel cost, and at least one
 * batch bottle would fill. Boundaries are checked at the default costs (3 ingredients, 2 fuel) and
 * at the config extremes (2–6 ingredients, 1–4 fuel).
 */
class BatchEngagementTest {

    @Test
    void engagesAtTheDefaultCostsWhenEverythingIsMet() {
        assertTrue(BatchBrew.engages(3, 2, 3, 2, 1));
    }

    @Test
    void shortIngredientsFallBackToNormal() {
        assertFalse(BatchBrew.engages(2, 20, 3, 2, 1));
    }

    @Test
    void shortFuelFallsBackToNormal() {
        assertFalse(BatchBrew.engages(3, 1, 3, 2, 1));
    }

    @Test
    void anEmptyOrUndiscoveredBatchRowDoesNotEngage() {
        assertFalse(BatchBrew.engages(6, 4, 3, 2, 0));
    }

    @Test
    void exactBoundariesEngage() {
        assertTrue(BatchBrew.engages(3, 2, 3, 2, 1)); // ingredient == cost, fuel == fuel cost
        assertTrue(BatchBrew.engages(6, 4, 6, 4, 3)); // config maxima, full batch row
    }

    @Test
    void higherConfiguredCostsRaiseTheBar() {
        assertFalse(BatchBrew.engages(5, 4, 6, 4, 1)); // 5 < cost 6
        assertTrue(BatchBrew.engages(6, 4, 6, 4, 1));
        assertFalse(BatchBrew.engages(6, 3, 6, 4, 1)); // 3 < fuel cost 4
    }
}
