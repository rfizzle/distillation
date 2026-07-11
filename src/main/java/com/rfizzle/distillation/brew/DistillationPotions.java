package com.rfizzle.distillation.brew;

import com.rfizzle.distillation.Distillation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The potion registrations for {@code design/SPEC.md} §2 — the five effect lines vanilla shipped
 * without recipes, plus the two corruption outputs. Every line is declared in {@link #LINES} and
 * registered mechanically from it, so the table is the single place the spec's durations live.
 *
 * <p>Each potion carries an explicit display-name string (the way vanilla's own {@code long_}/
 * {@code strong_} variants do), so all variants of a line share one
 * {@code item.minecraft.potion.effect.<name>} translation key — shipped in this mod's
 * {@code en_us.json} except {@code luck}, which vanilla already localizes.
 *
 * <p>Registrations are unconditional: with {@code enableMissingBrews=false} the conversions leave
 * the {@link com.rfizzle.distillation.recipe.RecipeGraph recipe graph} but existing bottles keep
 * working, per the spec's disabled contract.
 */
public final class DistillationPotions {

    /**
     * One potion line: a base and {@code long_} variant, plus a {@code strong_} (level II) variant
     * when {@code strongTicks} is positive. Durations in ticks, from the spec §2 table.
     */
    public record Line(String path, String name, Holder<MobEffect> effect,
                       int baseTicks, int longTicks, int strongTicks) {
        public boolean hasStrong() {
            return strongTicks > 0;
        }
    }

    public static final List<Line> LINES = List.of(
            new Line("resistance", "resistance", MobEffects.DAMAGE_RESISTANCE, 3600, 9600, 1800),
            new Line("haste", "haste", MobEffects.DIG_SPEED, 9600, 24000, 4800),
            new Line("absorption", "absorption", MobEffects.ABSORPTION, 3600, 9600, 1800),
            new Line("luck", "luck", MobEffects.LUCK, 9600, 24000, -1),
            new Line("glowing", "glowing", MobEffects.GLOWING, 3600, 9600, -1),
            // Corruption outputs (§2): Haste → Mining Fatigue, Luck → Bad Luck.
            new Line("mining_fatigue", "mining_fatigue", MobEffects.DIG_SLOWDOWN, 3600, 9600, -1),
            new Line("bad_luck", "bad_luck", MobEffects.UNLUCK, 9600, 24000, -1));

    private static final Map<String, Holder<Potion>> REGISTERED = new LinkedHashMap<>();
    private static boolean registered = false;

    private DistillationPotions() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (Line line : LINES) {
            registerPotion(line.path(),
                    new Potion(line.name(), new MobEffectInstance(line.effect(), line.baseTicks())));
            registerPotion("long_" + line.path(),
                    new Potion(line.name(), new MobEffectInstance(line.effect(), line.longTicks())));
            if (line.hasStrong()) {
                registerPotion("strong_" + line.path(),
                        new Potion(line.name(), new MobEffectInstance(line.effect(), line.strongTicks(), 1)));
            }
        }
    }

    /** The registered holder for a path from {@link #LINES} (e.g. {@code "long_haste"}). */
    public static Holder<Potion> potion(String path) {
        return Objects.requireNonNull(REGISTERED.get(path), () -> "potion not registered: " + path);
    }

    private static void registerPotion(String path, Potion potion) {
        REGISTERED.put(path, Registry.registerForHolder(BuiltInRegistries.POTION, Distillation.id(path), potion));
    }
}
