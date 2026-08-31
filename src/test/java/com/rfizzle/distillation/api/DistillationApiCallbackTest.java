// Tier: 1 (pure JUnit — array-backed Fabric events, no game bootstrap)
package com.rfizzle.distillation.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Concord API Standard §3.1 listener-isolation contract on both public callbacks: a
 * throwing listener is caught, logged once, and skipped, the listeners registered after it still
 * run, and a {@link VirtualMachineError} is rethrown rather than absorbed. Exercised through the
 * array-backed invoker directly — the listeners never dereference their arguments, so no game
 * state is needed.
 *
 * <p>Fabric {@code Event}s have no unregister, so registrations here are permanent for the test
 * JVM — one method per callback, asserting in order, so the fatal-error listener cannot leak into
 * the isolation assertion. No other unit test invokes either callback.
 */
class DistillationApiCallbackTest {

    @Test
    void brewCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();

        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) ->
                ran.add("first"));
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) -> {
            throw new IllegalStateException("guest is broken");
        });
        // AbstractMethodError is what a consumer compiled against an older signature raises — an
        // Exception catch would let it escape and kill the server tick.
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) -> {
            throw new AbstractMethodError("stale consumer");
        });
        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) ->
                ran.add("last"));

        DistillationBrewCallback.LISTENER_FAILURE_LOGGED.set(false);
        assertDoesNotThrow(() -> DistillationBrewCallback.EVENT.invoker()
                .onBrew(null, null, null, List.of(), null, false));
        assertEquals(List.of("first", "last"), ran,
                "a listener after a throwing one must still run");
        assertTrue(DistillationBrewCallback.LISTENER_FAILURE_LOGGED.get(),
                "the once-gate must arm when a guest listener throws");

        DistillationBrewCallback.EVENT.register((level, pos, ingredient, results, batchOwner, batch) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class, () -> DistillationBrewCallback.EVENT.invoker()
                .onBrew(null, null, null, List.of(), null, true));
    }

    @Test
    void discoveryCallbackIsolatesListenersButRethrowsFatalErrors() {
        List<String> ran = new ArrayList<>();
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "distillation", "fermented_spider_eye/thick");

        DistillationDiscoveryCallback.EVENT.register((player, id) -> ran.add("first"));
        DistillationDiscoveryCallback.EVENT.register((player, id) -> {
            throw new IllegalStateException("guest is broken");
        });
        DistillationDiscoveryCallback.EVENT.register((player, id) -> {
            throw new NoClassDefFoundError("stale consumer");
        });
        DistillationDiscoveryCallback.EVENT.register((player, id) -> ran.add("last"));

        DistillationDiscoveryCallback.LISTENER_FAILURE_LOGGED.set(false);
        assertDoesNotThrow(() -> DistillationDiscoveryCallback.EVENT.invoker().onDiscover(null, recipeId));
        assertEquals(List.of("first", "last"), ran,
                "a listener after a throwing one must still run");
        assertTrue(DistillationDiscoveryCallback.LISTENER_FAILURE_LOGGED.get(),
                "the once-gate must arm when a guest listener throws");

        DistillationDiscoveryCallback.EVENT.register((player, id) -> {
            throw new StackOverflowError("the JVM is gone, not the guest");
        });
        assertThrows(StackOverflowError.class,
                () -> DistillationDiscoveryCallback.EVENT.invoker().onDiscover(null, recipeId));
    }
}
