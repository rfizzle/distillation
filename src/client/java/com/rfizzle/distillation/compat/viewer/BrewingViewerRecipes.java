package com.rfizzle.distillation.compat.viewer;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.client.discovery.ClientDiscoveryState;
import com.rfizzle.distillation.recipe.RecipeGraph;
import com.rfizzle.distillation.recipe.RecipeGraphs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The viewer-agnostic snapshot every recipe-viewer plugin (EMI, REI, JEI) reads from — one place the
 * graph enumeration, discovery filter, and icon layout live, so the three adapters can't drift. Each
 * {@link Entry} is the input/ingredient/output triple a viewer draws.
 *
 * <p>The list is the current recipe graph's conversions ({@code design/SPEC.md} §1, including the
 * §2/§5/§6 lines), deduped by id, filtered to the viewing player's discoveries unless
 * {@code recipeViewerShowsUndiscovered} — the viewer never spoils what the stand teaches. Read on the
 * client thread from a live world; empty at the title screen.
 */
public final class BrewingViewerRecipes {

    /** One brewing conversion as a viewer draws it: input bottle, ingredient, output bottle. */
    public record Entry(ResourceLocation id, ItemStack input, ItemStack ingredient, ItemStack output) {
    }

    private BrewingViewerRecipes() {
    }

    public static List<Entry> snapshot() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of(); // no world: nothing to enumerate (viewers rebuild on join)
        }
        RecipeGraph graph = RecipeGraphs.forLevel(level);
        // Client-only preference, read from the local config directly (it never syncs from the server).
        boolean showUndiscovered = Distillation.getConfig().client.recipeViewerShowsUndiscovered;
        Set<ResourceLocation> discovered = showUndiscovered ? null : ClientDiscoveryState.discovered();
        return entriesFrom(graph.conversions(), discovered);
    }

    /**
     * The pure dedupe-and-filter core: {@code discovered == null} shows every conversion (the
     * {@code recipeViewerShowsUndiscovered} path), otherwise only ids in the set. Deduped by id, in
     * graph order. Extracted so the filter unit-tests over a synthetic graph without a client.
     */
    static List<Entry> entriesFrom(List<RecipeGraph.Conversion> conversions,
                                   @Nullable Set<ResourceLocation> discovered) {
        List<Entry> entries = new ArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (RecipeGraph.Conversion conversion : conversions) {
            ResourceLocation id = conversion.id();
            if (!seen.add(id)) {
                continue; // the graph builder is first-wins on a shared id; a viewer shows it once
            }
            if (discovered != null && !discovered.contains(id)) {
                continue; // undiscovered conversions stay hidden by default
            }
            entries.add(new Entry(id, inputStack(conversion), ingredientStack(conversion), outputStack(conversion)));
        }
        return List.copyOf(entries);
    }

    private static ItemStack inputStack(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.from());
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).from());
    }

    private static ItemStack ingredientStack(RecipeGraph.Conversion conversion) {
        return new ItemStack(conversion.ingredient());
    }

    private static ItemStack outputStack(RecipeGraph.Conversion conversion) {
        if (conversion instanceof RecipeGraph.PotionConversion potion) {
            return PotionContents.createItemStack(Items.POTION, potion.to());
        }
        return new ItemStack(((RecipeGraph.ContainerConversion) conversion).to());
    }
}
