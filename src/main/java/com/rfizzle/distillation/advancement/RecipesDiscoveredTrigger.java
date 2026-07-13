package com.rfizzle.distillation.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * Grants when a player's graph-resolvable discovery count reaches a threshold ({@code design/SPEC.md}
 * §11 — Scholar of the Still at ten). The count is evaluated against the live recipe graph at each
 * discovery, so stale ids never pad it. The predicate is a {@code >=} threshold, never equality, so a
 * bulk grant that jumps past the threshold still satisfies it.
 */
public class RecipesDiscoveredTrigger extends SimpleCriterionTrigger<RecipesDiscoveredTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Fired from the discovery observer with the player's current graph-resolvable discovery count. */
    public void trigger(ServerPlayer player, int discoveredCount) {
        this.trigger(player, instance -> instance.matches(discoveredCount));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minCount)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_count").forGetter(TriggerInstance::minCount)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forCount(int minCount) {
            return new TriggerInstance(Optional.empty(), minCount);
        }

        public boolean matches(int discoveredCount) {
            return discoveredCount >= minCount;
        }
    }
}
