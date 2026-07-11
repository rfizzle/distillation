package com.rfizzle.distillation.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * What a Murky Draught remembers ({@code design/SPEC.md} §1): the potion the failed bottle held,
 * and the hint ingredient the tooltip names — one ingredient that would have taken for that input,
 * absent when nothing brews onward from it (the hintless draught). The flicker is deliberately not
 * stored: it re-resolves from the live graph at drink time, so a conversion removed since bottling
 * degrades the drink to nausea alone instead of applying stale effects.
 */
public record MurkyDraughtContents(ResourceLocation inputPotion, Optional<ResourceLocation> hintIngredient) {

    public static final Codec<MurkyDraughtContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("input_potion").forGetter(MurkyDraughtContents::inputPotion),
            ResourceLocation.CODEC.optionalFieldOf("hint_ingredient")
                    .forGetter(MurkyDraughtContents::hintIngredient)
    ).apply(instance, MurkyDraughtContents::new));

    public static final StreamCodec<ByteBuf, MurkyDraughtContents> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, MurkyDraughtContents::inputPotion,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), MurkyDraughtContents::hintIngredient,
            MurkyDraughtContents::new);
}
