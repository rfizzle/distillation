// Tier: 1 (pure JUnit — array-backed Fabric events, no game bootstrap)
package com.rfizzle.distillation.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host-side error isolation SPEC §Public API promises: a {@code DistillationBrewCallback} or
 * {@code DistillationDiscoveryCallback} listener that throws is caught, logged, and skipped, and a
 * well-behaved listener registered after it still runs. Exercised through the array-backed invoker
 * directly — the listeners never dereference their arguments, so no game state is needed.
 */
class DistillationApiCallbackTest {

    @Test
    void brewCallbackSurvivesAThrowingListener() {
        AtomicBoolean laterRan = new AtomicBoolean(false);
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) -> {
            throw new RuntimeException("boom");
        });
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) ->
                laterRan.set(true));

        assertDoesNotThrow(() -> DistillationBrewCallback.EVENT.invoker()
                .onBrew(null, null, null, List.of(), null, false));
        assertTrue(laterRan.get(), "a listener after a throwing one must still run");
    }

    @Test
    void discoveryCallbackSurvivesAThrowingListener() {
        AtomicBoolean laterRan = new AtomicBoolean(false);
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("distillation", "fermented_spider_eye/thick");
        DistillationDiscoveryCallback.EVENT.register((player, id) -> {
            throw new RuntimeException("boom");
        });
        DistillationDiscoveryCallback.EVENT.register((player, id) -> laterRan.set(true));

        assertDoesNotThrow(() -> DistillationDiscoveryCallback.EVENT.invoker().onDiscover(null, recipeId));
        assertTrue(laterRan.get(), "a listener after a throwing one must still run");
    }
}
