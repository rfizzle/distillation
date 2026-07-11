package com.rfizzle.distillation.sound;

import com.rfizzle.distillation.Distillation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's custom sound events ({@code design/SPEC.md} §Sound Design) — synthesized cues through
 * the {@code .sfx} pipeline, each with a {@code sounds.json} entry and a subtitle key.
 */
public final class DistillationSounds {

    /** Bright two-tone rising chime, played to the discovering player on first-time discovery. */
    public static final SoundEvent RECIPE_LEARNED =
            SoundEvent.createVariableRangeEvent(Distillation.id("ui.recipe_learned"));

    /** Dull, damp fizzle, played at the stand when a brew cycle bottles a Murky Draught. */
    public static final SoundEvent MURKY_FIZZLE =
            SoundEvent.createVariableRangeEvent(Distillation.id("block.brewing_stand.murky"));

    private DistillationSounds() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, Distillation.id("ui.recipe_learned"), RECIPE_LEARNED);
        Registry.register(BuiltInRegistries.SOUND_EVENT, Distillation.id("block.brewing_stand.murky"), MURKY_FIZZLE);
    }
}
