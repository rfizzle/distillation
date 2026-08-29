package com.rfizzle.distillation.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Distillation's {@code fabric-datagen} entrypoint — the first of the four datagen anchors
 * (the loom {@code datagen} run, the {@code make run-datagen} target, and
 * {@code verifyDatagenIdempotent} are the other three, and they only mean anything as a set).
 * Distillation held 2, 3, and 4 without this class, which is the worst of the four states:
 * {@code verifyDatagenIdempotent} shells out to {@code git status --porcelain -- src/main/generated},
 * and with no entrypoint that directory was never created, so the pathspec matched nothing, git
 * exited 0 with empty output, and a green CI step verified nothing at all.
 *
 * <p>Everything registered here writes into {@code src/main/generated}, which {@code build.gradle}
 * declares as a {@code main} resources source dir — so the output ships in the jar and lands on the
 * test classpath exactly the way {@code src/main/resources} does, which is what keeps the
 * {@code *ResourceContractTest} guards reading the real artifact.
 *
 * <p>What deliberately stays hand-authored under {@code src/main/resources}, and why:
 *
 * <ul>
 *   <li>{@code assets/distillation/models/item/flask.json} and
 *       {@code assets/minecraft/models/item/potion.json} carry {@code overrides} blocks keyed on
 *       Distillation's custom item properties ({@code distillation:filled},
 *       {@code distillation:draught}, {@code distillation:antidote}). No vanilla
 *       {@link net.minecraft.data.models.model.ModelTemplate} emits an {@code overrides} array, and
 *       the second file is in the {@code minecraft} namespace besides — it replaces vanilla's own
 *       potion model.</li>
 *   <li>{@code models/item/antidote.json}, {@code flask_filled.json}, and {@code potion_half.json}
 *       are override <em>targets</em>, not models of registered items.
 *       {@link net.minecraft.data.models.ItemModelGenerators#generateFlatItem} derives its output
 *       path from an item's registry id, and these three name no item.</li>
 *   <li>Lang, sounds, sound subtitles, textures, the mixin configs, the access widener, and the
 *       manifest are all hand-authored surfaces datagen has no provider for.</li>
 * </ul>
 */
public class DistillationDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(DistillationModelProvider::new);
        pack.addProvider(DistillationRecipeProvider::new);
        pack.addProvider(DistillationAdvancementProvider::new);
    }
}
