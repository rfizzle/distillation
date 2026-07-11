package com.rfizzle.distillation.item;

import com.rfizzle.distillation.Distillation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
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

    public static final Item MURKY_DRAUGHT = new MurkyDraughtItem(new Item.Properties().stacksTo(1));

    private DistillationItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("murky_draught_contents"),
                MURKY_DRAUGHT_CONTENTS);
        Registry.register(BuiltInRegistries.ITEM, Distillation.id("murky_draught"), MURKY_DRAUGHT);
    }
}
