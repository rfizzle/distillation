package com.rfizzle.distillation.discovery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The write choke point for a stand's {@link BrewProvenance} attachment: every mutation lands
 * here, marks the block entity changed (in-place attachment writes do not auto-dirty a block
 * entity), and removes the attachment outright when empty so idle stands stay latent.
 */
public final class BrewProvenances {

    private BrewProvenances() {
    }

    /**
     * The brew seam's per-cycle write: slots this cycle converted record their new conversion;
     * slots it murked drop any earlier record — the bottle there is a Murky Draught now, not the
     * old brew's untaken output; other slots keep any existing record — an unmatched bottle
     * passes through a cycle unchanged, so an earlier brew's untaken output is still exactly what
     * its record says.
     */
    public static void recordBrew(BrewingStandBlockEntity stand, Map<Integer, ResourceLocation> produced,
                                  Set<Integer> murked) {
        BrewProvenance existing = stand.getAttached(DistillationAttachments.BREW_PROVENANCE);
        Map<Integer, ResourceLocation> merged = new LinkedHashMap<>(
                existing == null ? Map.of() : existing.bySlot());
        murked.forEach(merged::remove);
        merged.putAll(produced);
        set(stand, new BrewProvenance(merged));
    }

    /**
     * Consumes one slot's provenance: returns the recipe id that produced the bottle there (empty
     * when none is recorded) and clears the entry either way, so a later foreign bottle in the
     * same slot can never ride an old brew's record.
     */
    public static Optional<ResourceLocation> take(BrewingStandBlockEntity stand, int slot) {
        BrewProvenance provenance = stand.getAttached(DistillationAttachments.BREW_PROVENANCE);
        if (provenance == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> recipeId = provenance.forSlot(slot);
        if (recipeId.isPresent()) {
            set(stand, provenance.without(slot));
        }
        return recipeId;
    }

    private static void set(BrewingStandBlockEntity stand, BrewProvenance provenance) {
        if (provenance.isEmpty()) {
            stand.removeAttached(DistillationAttachments.BREW_PROVENANCE);
        } else {
            stand.setAttached(DistillationAttachments.BREW_PROVENANCE, provenance);
        }
        stand.setChanged();
    }
}
