package com.rfizzle.distillation.network;

import com.rfizzle.distillation.Distillation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S request to copy a discovered recipe onto paper ({@code design/SPEC.md} §1): the client sends
 * the recipe id its recipes-page row points at, and the server re-validates everything before
 * minting the note ({@link com.rfizzle.distillation.item.RecipeNoteServerHandler}). The payload
 * carries only an id — a client that sends one for a recipe it never discovered is refused
 * server-side, so this is a request, never an authority.
 */
public record CopyRecipeNotePayload(ResourceLocation recipeId) implements CustomPacketPayload {

    public static final Type<CopyRecipeNotePayload> TYPE =
            new Type<>(Distillation.id("copy_recipe_note"));

    public static final StreamCodec<FriendlyByteBuf, CopyRecipeNotePayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, CopyRecipeNotePayload::recipeId,
                    CopyRecipeNotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
