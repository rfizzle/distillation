// Tier: 2 (fabric-loader-junit + Bootstrap — the instance codecs reference vanilla predicate codecs)
package com.rfizzle.distillation.advancement;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two parameterized §9 criterion instances: their thresholds are inclusive-and-floored
 * ({@code >=}, never equality), an absent field passes, and each instance shape round-trips through
 * its codec. The vanilla {@link net.minecraft.advancements.critereon.PlayerTrigger} milestones carry
 * no predicate, so their grants are proved in the gametest rather than here.
 */
class AdvancementCriteriaTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void discoveryCountIsAnInclusiveFlooredThreshold() {
        RecipesDiscoveredTrigger.TriggerInstance ten = RecipesDiscoveredTrigger.TriggerInstance.forCount(10);
        assertFalse(ten.matches(9), "nine discoveries do not reach ten");
        assertTrue(ten.matches(10), "the threshold count grants (>=, inclusive)");
        assertTrue(ten.matches(25), "a bulk jump past the threshold still grants");
    }

    @Test
    void missingLineMatchesItsLineAndAbsentPasses() {
        MissingLineBrewedTrigger.TriggerInstance resistance =
                MissingLineBrewedTrigger.TriggerInstance.forLine("resistance");
        assertTrue(resistance.matches("resistance"), "the resistance criterion matches a resistance brew");
        assertFalse(resistance.matches("haste"), "the resistance criterion rejects a haste brew");

        MissingLineBrewedTrigger.TriggerInstance any =
                new MissingLineBrewedTrigger.TriggerInstance(java.util.Optional.empty(), java.util.Optional.empty());
        assertTrue(any.matches("glowing"), "an absent line passes for any brewed line");
    }

    @Test
    void discoveryCountInstanceRoundTrips() {
        RecipesDiscoveredTrigger.TriggerInstance original = RecipesDiscoveredTrigger.TriggerInstance.forCount(10);
        var json = RecipesDiscoveredTrigger.TriggerInstance.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        RecipesDiscoveredTrigger.TriggerInstance decoded = RecipesDiscoveredTrigger.TriggerInstance.CODEC
                .parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(original, decoded, "the min_count instance survives a codec round trip");
    }

    @Test
    void missingLineInstanceRoundTrips() {
        MissingLineBrewedTrigger.TriggerInstance original =
                MissingLineBrewedTrigger.TriggerInstance.forLine("absorption");
        var json = MissingLineBrewedTrigger.TriggerInstance.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        MissingLineBrewedTrigger.TriggerInstance decoded = MissingLineBrewedTrigger.TriggerInstance.CODEC
                .parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(original, decoded, "the line instance survives a codec round trip");
    }
}
