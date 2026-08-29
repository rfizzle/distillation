package com.rfizzle.distillation.data;

import com.rfizzle.distillation.Distillation;
import com.rfizzle.distillation.advancement.DistillationCriteria;
import com.rfizzle.distillation.advancement.MissingLineBrewedTrigger;
import com.rfizzle.distillation.advancement.RecipesDiscoveredTrigger;
import com.rfizzle.distillation.item.DistillationItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Distillation's seven advancements ({@code design/SPEC.md} §11). Every one hangs off vanilla's
 * Local Brewery ({@code minecraft:nether/brew_potion}) rather than rooting a tab of its own —
 * brewing already has a home in the vanilla tree and the mod is an overhaul of it, not a parallel
 * progression.
 *
 * <p>The trigger ids these reference come from {@link DistillationCriteria}, whose
 * {@code register()} is idempotent precisely so it can be reached from the datagen server bootstrap
 * as well as from {@code onInitialize}.
 */
public class DistillationAdvancementProvider extends FabricAdvancementProvider {

    /** Every Distillation advancement hangs off vanilla's Local Brewery. */
    private static final ResourceLocation PARENT =
            ResourceLocation.withDefaultNamespace("nether/brew_potion");

    /** Scholar of the Still's discovery threshold ({@code design/SPEC.md} §11). */
    private static final int SCHOLAR_THRESHOLD = 10;

    /** The §2 effect lines The Missing Shelf demands, in the order the criteria are declared. */
    private static final List<String> MISSING_LINES = List.of(
            "resistance", "haste", "absorption", "luck", "glowing", "levitation", "health_boost");

    protected DistillationAdvancementProvider(FabricDataOutput output,
                                              CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup,
                                    Consumer<AdvancementHolder> consumer) {
        DistillationCriteria.register();

        one(consumer, "trial_and_error", new ItemStack(DistillationItems.MURKY_DRAUGHT),
                AdvancementType.TASK, false,
                "murky_bottled", playerDid(DistillationCriteria.MURKY_BOTTLED));

        one(consumer, "scholar_of_the_still", new ItemStack(Items.BOOK),
                AdvancementType.TASK, false,
                "discovered_ten", DistillationCriteria.RECIPES_DISCOVERED.createCriterion(
                        RecipesDiscoveredTrigger.TriggerInstance.forCount(SCHOLAR_THRESHOLD)));

        one(consumer, "surgical", antidoteIcon(),
                AdvancementType.TASK, false,
                "antidote_surgical", playerDid(DistillationCriteria.ANTIDOTE_SURGICAL));

        one(consumer, "the_good_stuff", new ItemStack(Items.GLOWSTONE_DUST),
                AdvancementType.GOAL, false,
                "premium_brewed", playerDid(DistillationCriteria.PREMIUM_BREWED));

        one(consumer, "round_for_the_table", new ItemStack(Items.CAULDRON),
                AdvancementType.GOAL, false,
                "batch_completed", playerDid(DistillationCriteria.BATCH_COMPLETED));

        // Every Drop is the only advancement that announces itself in chat — completing the whole
        // graph is the mod's terminal milestone, and the only one worth telling a server about.
        one(consumer, "every_drop", new ItemStack(Items.EXPERIENCE_BOTTLE),
                AdvancementType.CHALLENGE, true,
                "graph_completed", playerDid(DistillationCriteria.GRAPH_COMPLETED));

        // The Missing Shelf takes one criterion per §2 line, so the default AND requirement
        // strategy yields the "brew every one of them" conjunction. Declared in the MISSING_LINES
        // order, which the emitted `requirements` array preserves.
        Advancement.Builder missingShelf = builder("the_missing_shelf", new ItemStack(Items.BREWING_STAND),
                AdvancementType.GOAL, false);
        for (String line : MISSING_LINES) {
            missingShelf.addCriterion(line, DistillationCriteria.MISSING_LINE_BREWED.createCriterion(
                    MissingLineBrewedTrigger.TriggerInstance.forLine(line)));
        }
        missingShelf.save(consumer, Distillation.id("the_missing_shelf").toString());
    }

    /** One single-criterion advancement. */
    private static void one(Consumer<AdvancementHolder> consumer,
                            String name,
                            ItemStack icon,
                            AdvancementType type,
                            boolean announceToChat,
                            String criterionName,
                            Criterion<?> criterion) {
        builder(name, icon, type, announceToChat)
                .addCriterion(criterionName, criterion)
                .save(consumer, Distillation.id(name).toString());
    }

    /**
     * A parented, displayed builder with telemetry left off.
     *
     * <p>{@link Advancement.Builder#advancement()} turns {@code sendsTelemetryEvent} <em>on</em>;
     * every Distillation advancement has always shipped it off. The flag is inert for modded
     * content — its only reader, {@code WorldSessionTelemetryManager}, gates on the advancement id
     * being in the {@code minecraft} namespace — so this is not a correctness fix. It is simply
     * that a conversion whose job is to reproduce what ships should not flip a shipped field on the
     * way past, and the bare constructor is the same builder with the flag left alone.
     *
     * <p>The {@code background} argument is null on purpose: a background texture belongs to the
     * root of a tab, and these are children of a vanilla root.
     */
    // parent(ResourceLocation) is @Deprecated(forRemoval) in favour of parent(AdvancementHolder),
    // but the holder form can only name an advancement this provider itself built. These seven hang
    // off a vanilla advancement, so the id form is the only way to say it — vanilla's own providers
    // reach their parents as holders because they generate the whole tree.
    @SuppressWarnings("removal")
    private static Advancement.Builder builder(String name, ItemStack icon, AdvancementType type,
                                               boolean announceToChat) {
        return new Advancement.Builder()
                .parent(PARENT)
                .display(icon, title(name), description(name), null, type, true, announceToChat, false);
    }

    /** A bare "this player did the thing" criterion on one of Distillation's own triggers. */
    private static Criterion<PlayerTrigger.TriggerInstance> playerDid(PlayerTrigger trigger) {
        return trigger.createCriterion(new PlayerTrigger.TriggerInstance(Optional.empty()));
    }

    /**
     * Surgical's icon: a potion bottle already holding the Poison Antidote, so the tab shows the
     * tinted antidote sprite rather than a generic bottle. The holder is read from the live registry
     * because {@code Antidotes} registers its potions during {@code onInitialize}, which the datagen
     * server bootstrap runs before any provider does.
     */
    private static ItemStack antidoteIcon() {
        Holder<Potion> antidote = BuiltInRegistries.POTION
                .getHolder(ResourceKey.create(Registries.POTION, Distillation.id("poison_antidote")))
                .orElseThrow(() -> new IllegalStateException(
                        "distillation:poison_antidote is not registered — Antidotes.register() must run "
                                + "before advancement datagen"));
        ItemStack icon = new ItemStack(Items.POTION);
        icon.set(DataComponents.POTION_CONTENTS, new PotionContents(antidote));
        return icon;
    }

    private static Component title(String name) {
        return Component.translatable("advancements.distillation." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.distillation." + name + ".description");
    }
}
