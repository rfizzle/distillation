package com.rfizzle.distillation.item;

import com.mojang.serialization.Codec;
import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * The mod's registered items and their data components ({@code design/SPEC.md} §1, §12): the Murky
 * Draught, the Recipe Note, and the Flask. Registration is unconditional — the feature toggles
 * ({@code enableMurkyDraughts}, {@code enableRecipeNotes}, {@code enableFlask}) gate production at the
 * brew seam, the copy seam, and the pour seam, so bottled draughts, copied notes, and filled flasks
 * keep working when a feature is later switched off.
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

    /**
     * The recipe a note points at ({@code design/SPEC.md} §1): the stable recipe id its tooltip
     * resolves against the live graph to show {@code input + ingredient → output}. Persisted and
     * synced so the note reads the same in a chest, a trade, or a friend's hand.
     */
    public static final DataComponentType<ResourceLocation> NOTED_RECIPE =
            DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .cacheEncoding()
                    .build();

    /**
     * How many half-doses a flask holds ({@code design/SPEC.md} §12): a full dose is two half-units,
     * so a full flask stores {@link Flask#MAX_HALVES}; an odd value carries a pending sipped half. The
     * brew itself rides the vanilla {@link net.minecraft.core.component.DataComponents#POTION_CONTENTS}
     * on the same stack, so the flask reuses vanilla's effect, color, and honest-duration machinery.
     * Persisted and synced so the fill state and tooltip render on every client and survive save/load.
     */
    public static final DataComponentType<Integer> FLASK_DOSES =
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding()
                    .build();

    public static final Item MURKY_DRAUGHT = new MurkyDraughtItem(new Item.Properties().stacksTo(1));

    /** The copied recipe on paper ({@code design/SPEC.md} §1): tradeable, giftable, never a grant. */
    public static final Item RECIPE_NOTE = new RecipeNoteItem(new Item.Properties());

    /** The multi-dose vessel ({@code design/SPEC.md} §12): copper and glass, three doses of one brew. */
    public static final Item FLASK = new FlaskItem(new Item.Properties().stacksTo(1));

    private DistillationItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("murky_draught_contents"),
                MURKY_DRAUGHT_CONTENTS);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("draught"), DRAUGHT);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("noted_recipe"), NOTED_RECIPE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Distillation.id("flask_doses"), FLASK_DOSES);
        Registry.register(BuiltInRegistries.ITEM, Distillation.id("murky_draught"), MURKY_DRAUGHT);
        Registry.register(BuiltInRegistries.ITEM, Distillation.id("recipe_note"), RECIPE_NOTE);
        Registry.register(BuiltInRegistries.ITEM, Distillation.id("flask"), FLASK);
        // The flask is the one crafted item, so it earns a home in the creative inventory beside
        // vanilla's drinks; the gameplay-produced Murky Draught and Recipe Note stay tab-less. The
        // vanilla tab keys are private under Mojang mappings, so address the tab by its id.
        ResourceKey<CreativeModeTab> foodAndDrinks = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("food_and_drinks"));
        ItemGroupEvents.modifyEntriesEvent(foodAndDrinks).register(entries -> entries.accept(FLASK));
    }
}
