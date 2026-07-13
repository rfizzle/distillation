// Tier: 2 (fabric-loader-junit)
package com.rfizzle.distillation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wire-codec coverage for {@link CopyRecipeNotePayload}, the mod's one C2S request: the recipe id
 * round-trips and the codec consumes exactly what it wrote. No registry bootstrap needed — the id is
 * a plain identifier over {@link FriendlyByteBuf}.
 */
class CopyRecipeNotePayloadCodecTest {

    @Test
    void recipeIdRoundTrips() {
        CopyRecipeNotePayload original = new CopyRecipeNotePayload(
                ResourceLocation.fromNamespaceAndPath("distillation", "shulker_shell/awkward"));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CopyRecipeNotePayload.CODEC.encode(buf, original);
        CopyRecipeNotePayload decoded = CopyRecipeNotePayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes(), "codec should consume every byte it wrote");
    }

    @Test
    void namespacedIdRoundTrips() {
        CopyRecipeNotePayload original = new CopyRecipeNotePayload(
                ResourceLocation.fromNamespaceAndPath("minecraft", "gunpowder/water"));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CopyRecipeNotePayload.CODEC.encode(buf, original);

        assertEquals(original, CopyRecipeNotePayload.CODEC.decode(buf));
    }
}
