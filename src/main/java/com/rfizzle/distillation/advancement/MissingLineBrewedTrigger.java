package com.rfizzle.distillation.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Grants a criterion when a player brews (and takes) one of the §2 "missing brew" effect lines
 * ({@code design/SPEC.md} §10 — The Missing Shelf brews all five). One registered trigger backs the
 * whole set: the advancement declares five criteria, each pinning a {@code line}, and a fire with
 * that line satisfies exactly its criterion. An absent {@code line} passes for any line — the
 * "brewed any missing line" shape — mirroring vanilla's collect-them-all predicates.
 */
public class MissingLineBrewedTrigger extends SimpleCriterionTrigger<MissingLineBrewedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Fired from the extraction observer with the §2 line path (e.g. {@code "resistance"}) just taken. */
    public void trigger(ServerPlayer player, String line) {
        this.trigger(player, instance -> instance.matches(line));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> line)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("line").forGetter(TriggerInstance::line)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forLine(String line) {
            return new TriggerInstance(Optional.empty(), Optional.of(line));
        }

        public boolean matches(String brewedLine) {
            return line.isEmpty() || line.get().equals(brewedLine);
        }
    }
}
