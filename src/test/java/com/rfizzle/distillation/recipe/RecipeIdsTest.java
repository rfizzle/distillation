// Tier: 1 (pure JUnit — ResourceLocation is a plain value type, no bootstrap)
package com.rfizzle.distillation.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins SPEC §1's stable-id scheme: {@code distillation:<ingredient segment>/<input segment>},
 * bare paths for the {@code minecraft} namespace, {@code <namespace>/<path>} segments otherwise.
 * These ids become permanent player discovery data — a drift here silently orphans saves.
 */
class RecipeIdsTest {

    @Test
    void minecraftNamespacesUseBarePaths() {
        assertEquals(rl("distillation:shulker_shell/awkward"), RecipeIds.derive(
                rl("minecraft:shulker_shell"), rl("minecraft:awkward")));
    }

    @Test
    void foreignIngredientNamespaceIsPrefixed() {
        assertEquals(rl("distillation:somemod/rootbulb/awkward"), RecipeIds.derive(
                rl("somemod:rootbulb"), rl("minecraft:awkward")));
    }

    @Test
    void foreignInputNamespaceIsPrefixed() {
        assertEquals(rl("distillation:redstone/distillation/haste"), RecipeIds.derive(
                rl("minecraft:redstone"), rl("distillation:haste")));
    }

    @Test
    void bothNamespacesPrefixIndependently() {
        assertEquals(rl("distillation:somemod/rootbulb/othermod/tonic"), RecipeIds.derive(
                rl("somemod:rootbulb"), rl("othermod:tonic")));
    }

    @Test
    void containerConversionsUseTheContainerItemAsInput() {
        assertEquals(rl("distillation:gunpowder/potion"), RecipeIds.derive(
                rl("minecraft:gunpowder"), rl("minecraft:potion")));
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.parse(id);
    }
}
