package com.rfizzle.distillation.brew;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Map;

/**
 * The utility-potion duration retune of {@code design/SPEC.md} §4: an internal map (vanilla potion
 * id → effect duration in ticks) applied at the single seam where {@code PotionContents}
 * instantiates its {@link MobEffectInstance}s (see
 * {@link com.rfizzle.distillation.mixin.PotionContentsMixin}), so loot, traded, creative-picked, and
 * brewed bottles all retune identically and nothing is baked into the item.
 *
 * <p>Distillation's own §2 lines ({@link DistillationPotions}) bake their durations at registration
 * under {@code distillation:} ids, which are absent from this {@code minecraft:}-only table — so they
 * are exempt from double-scaling by construction.
 */
public final class HonestDurations {

    /** SPEC §4 table: utility potion id → effect duration in ticks. */
    public static final Map<ResourceLocation, Integer> OVERRIDES = Map.ofEntries(
            entry("fire_resistance", 9600), entry("long_fire_resistance", 24000),
            entry("water_breathing", 9600), entry("long_water_breathing", 24000),
            entry("night_vision", 9600), entry("long_night_vision", 24000),
            entry("invisibility", 9600), entry("long_invisibility", 24000),
            entry("slow_falling", 4800), entry("long_slow_falling", 12000));

    private HonestDurations() {
    }

    private static Map.Entry<ResourceLocation, Integer> entry(String path, int ticks) {
        return Map.entry(ResourceLocation.withDefaultNamespace(path), ticks);
    }

    /** The retuned duration for a potion id, or {@code -1} when the id is not a §4 utility line. */
    public static int durationFor(ResourceLocation potionId) {
        Integer ticks = OVERRIDES.get(potionId);
        return ticks != null ? ticks : -1;
    }

    /** ⌊ticks ÷ 2⌋ — a draught's half, floored (SPEC §4); durations are never negative. */
    public static int half(int ticks) {
        return ticks / 2;
    }

    /**
     * A fresh copy of {@code instance} at {@code ticks} duration, preserving amplifier, ambient,
     * visible, and show-icon flags. Never mutates the source — the callers hand this to a consumer
     * or a copy list, never into the shared registered {@code Potion}'s effect list.
     */
    public static MobEffectInstance withDuration(MobEffectInstance instance, int ticks) {
        return new MobEffectInstance(instance.getEffect(), ticks, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }
}
