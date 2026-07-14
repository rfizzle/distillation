// Tier: 1 (pure JUnit — no Fabric runtime)
package com.rfizzle.distillation.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 — the pure fill gate of {@code design/SPEC.md} §12: the discovery gate (discovery off allows
 * any brew; on, a producer must be discovered and a producerless brew fails closed) and the pour
 * gate (feature on, room left, brew compatible, and a discovered producer).
 */
class FlaskFillTest {

    @Test
    void discoveryOffAllowsAnyBrew() {
        assertTrue(FlaskFill.discoveredProducer(false, false, 0));
    }

    @Test
    void discoveryOnRequiresADiscoveredProducer() {
        assertTrue(FlaskFill.discoveredProducer(true, true, 2));
        assertFalse(FlaskFill.discoveredProducer(true, false, 2));  // has producers, none discovered
        assertFalse(FlaskFill.discoveredProducer(true, true, 0));   // no producer at all — fail closed
    }

    @Test
    void pourAllowedNeedsEveryCondition() {
        assertTrue(FlaskFill.pourAllowed(true, false, true, true));
        assertFalse(FlaskFill.pourAllowed(false, false, true, true)); // feature off
        assertFalse(FlaskFill.pourAllowed(true, true, true, true));   // flask full
        assertFalse(FlaskFill.pourAllowed(true, false, false, true)); // brew mismatch
        assertFalse(FlaskFill.pourAllowed(true, false, true, false)); // undiscovered
    }
}
