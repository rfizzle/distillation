package com.rfizzle.distillation.discovery;

import com.rfizzle.distillation.Distillation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * The mod's Fabric data attachments. Both are latent — a player who has never discovered a recipe
 * and a stand that has never brewed carry no attachment bytes at all, so untouched saves stay
 * byte-identical to vanilla.
 */
public final class DistillationAttachments {

    /** Per-player permanent discovery set; survives death (copy-on-death), relog, and dimension change. */
    public static final AttachmentType<DiscoveryData> DISCOVERY = AttachmentRegistry.create(
            Distillation.id("discovery"),
            builder -> builder
                    .persistent(DiscoveryData.CODEC)
                    .copyOnDeath()
                    .initializer(DiscoveryData::new));

    /** Per-stand brew provenance; written by the brew seam, consumed by the extraction hook. */
    public static final AttachmentType<BrewProvenance> BREW_PROVENANCE = AttachmentRegistry.create(
            Distillation.id("brew_provenance"),
            builder -> builder.persistent(BrewProvenance.CODEC));

    private DistillationAttachments() {
    }

    /** Forces class load so the attachment types register during mod init. */
    public static void init() {
    }
}
