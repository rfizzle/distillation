// Tier: 1 (pure JUnit — codec round-trips over plain ops and buffers, no registry bootstrap)
package com.rfizzle.distillation.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Persistence and wire fidelity for {@link MurkyDraughtContents}: the component must survive item
 * NBT (world save) and the S2C component sync (the client tooltip reads it), for both the hinted
 * and hintless shapes.
 */
class MurkyDraughtContentsTest {

    private static final MurkyDraughtContents HINTED = new MurkyDraughtContents(
            ResourceLocation.parse("minecraft:awkward"),
            Optional.of(ResourceLocation.parse("minecraft:shulker_shell")));
    private static final MurkyDraughtContents HINTLESS = new MurkyDraughtContents(
            ResourceLocation.parse("distillation:strong_haste"), Optional.empty());

    @Test
    void codecRoundTripsHintedAndHintless() {
        for (MurkyDraughtContents original : new MurkyDraughtContents[]{HINTED, HINTLESS}) {
            JsonElement encoded = MurkyDraughtContents.CODEC.encodeStart(JsonOps.INSTANCE, original)
                    .getOrThrow();
            MurkyDraughtContents decoded = MurkyDraughtContents.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow();
            assertEquals(original, decoded);
        }
    }

    @Test
    void hintlessEncodingOmitsTheHintField() {
        JsonObject encoded = MurkyDraughtContents.CODEC.encodeStart(JsonOps.INSTANCE, HINTLESS)
                .getOrThrow().getAsJsonObject();
        assertFalse(encoded.has("hint_ingredient"),
                "an absent hint serializes as an absent field, not a sentinel");
    }

    @Test
    void streamCodecRoundTripsHintedAndHintless() {
        for (MurkyDraughtContents original : new MurkyDraughtContents[]{HINTED, HINTLESS}) {
            ByteBuf buf = Unpooled.buffer();
            MurkyDraughtContents.STREAM_CODEC.encode(buf, original);
            MurkyDraughtContents decoded = MurkyDraughtContents.STREAM_CODEC.decode(buf);
            assertEquals(original, decoded);
            assertEquals(0, buf.readableBytes(), "codec should consume every byte it wrote");
        }
    }
}
