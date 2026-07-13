package com.rfizzle.distillation.advancement;

import com.rfizzle.distillation.Distillation;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The custom advancement criterion triggers of {@code design/SPEC.md} §10, registered into
 * {@link BuiltInRegistries#TRIGGER_TYPES} under {@code distillation:} ids. Five milestones are plain
 * "player did the thing" moments and reuse vanilla's {@link PlayerTrigger} under our own ids; two
 * carry a magnitude (a discovery count, a §2 line) and get a bespoke trigger.
 *
 * <p>{@link #register()} is idempotent because it is reached from {@code onInitialize}, from the
 * datagen server bootstrap, and from the gametest server bootstrap — a second {@code Registry.register}
 * of the same id would throw. It must run before any advancement JSON referencing these ids is
 * deserialized (server start), which {@code onInitialize} guarantees.
 */
public final class DistillationCriteria {

    /** Bottling a Murky Draught from a failed brew — Trial and Error. */
    public static final PlayerTrigger MURKY_BOTTLED = new PlayerTrigger();
    /** Taking an extended-and-amplified premium potion — The Good Stuff. */
    public static final PlayerTrigger PREMIUM_BREWED = new PlayerTrigger();
    /** Completing a six-bottle batch pass — Round for the Table. */
    public static final PlayerTrigger BATCH_COMPLETED = new PlayerTrigger();
    /** Discovering every recipe in the live graph — Every Drop. */
    public static final PlayerTrigger GRAPH_COMPLETED = new PlayerTrigger();
    /** An antidote strips one effect while ≥2 others remain — Surgical. */
    public static final PlayerTrigger ANTIDOTE_SURGICAL = new PlayerTrigger();
    /** Reaching a discovery-count threshold — Scholar of the Still (ten). */
    public static final RecipesDiscoveredTrigger RECIPES_DISCOVERED = new RecipesDiscoveredTrigger();
    /** Brewing one of the §2 effect lines — The Missing Shelf (all of them). */
    public static final MissingLineBrewedTrigger MISSING_LINE_BREWED = new MissingLineBrewedTrigger();

    private static boolean registered = false;

    private DistillationCriteria() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("murky_bottled"), MURKY_BOTTLED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("premium_brewed"), PREMIUM_BREWED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("batch_completed"), BATCH_COMPLETED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("graph_completed"), GRAPH_COMPLETED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("antidote_surgical"), ANTIDOTE_SURGICAL);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("recipes_discovered"), RECIPES_DISCOVERED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Distillation.id("missing_line_brewed"), MISSING_LINE_BREWED);
    }
}
