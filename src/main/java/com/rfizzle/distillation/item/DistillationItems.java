package com.rfizzle.distillation.item;

import com.mojang.serialization.Codec;
import com.rfizzle.distillation.Distillation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;

/**
 * The mod's registered items and their data components ({@code design/SPEC.md}'s one item: the
 * Murky Draught). Registration is unconditional — {@code enableMurkyDraughts} gates production at
 * the brew seam, so bottled draughts keep working when the feature is later switched off.
 */
public final class DistillationItems {

    /** The failed bottle's memory: input potion + hint ingredient, synced for the tooltip. */
    public static final DataComponentType<MurkyDraughtContents> MURKY_DRAUGHT_CONTENTS =
            DataComponentType.<MurkyDraughtContents>builder()
                    .persistent(MurkyDraughtContents.CODEC)
                    .networkSynchronized(MurkyDraughtContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build();

    /**
     * A half draught marker ({@code design/SPEC.md} §4): present (always {@code true}) when a potion
     * has been sipped down to its remaining half. Synced and persisted so the half-empty model,
     * "(Half)" name, and halved tooltip render client-side and survive save/load; the drink applies
     * ⌊duration ÷ 2⌋ at drink time, so nothing about the duration is stored here. Registration is
     * unconditional — {@code enableDraughts} gates only new sipping, so existing halves stay drinkable.
     */
    public static final DataComponentType<Boolean> DRAUGHT =
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .cacheEncoding()
                    .build();

    public static final Item MURKY_DRAUGHT = new MurkyDraughtItem(new Item.Properties().stacksTo(1));

    private DistillationItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("murky_draught_contents"),
                MURKY_DRAUGHT_CONTENTS);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("draught"), DRAUGHT);
        Registry.register(BuiltInRegistries.ITEM, Distillation.id("murky_draught"), MURKY_DRAUGHT);
    }
}
