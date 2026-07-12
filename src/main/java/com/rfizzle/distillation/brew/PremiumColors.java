package com.rfizzle.distillation.brew;

/**
 * Pure color math for the "deeper liquid color" a concentrated or premium bottle carries
 * ({@code design/SPEC.md} §5): a concentrated potion has stats identical to its base, so vanilla's
 * effect-derived tint would compute the same color — the deepening is the whole visual signal.
 * Kept Minecraft-free so the darkening is unit-tested on its own; {@link
 * com.rfizzle.distillation.mixin.PotionContentsColorMixin} applies it derived-at-read, never stored.
 */
public final class PremiumColors {

    /** Each RGB channel scales toward black by this factor — a visibly deeper liquid, not muddy. */
    public static final float DEEPEN_FACTOR = 0.65F;

    private PremiumColors() {
    }

    /**
     * Deepens a packed color by scaling each RGB channel toward black, preserving the top byte
     * (alpha) untouched. Deterministic and idempotent-free — callers apply it exactly once, at the
     * single {@code getColor} read seam.
     */
    public static int deepen(int color) {
        int alpha = color & 0xFF000000;
        int r = Math.round(((color >> 16) & 0xFF) * DEEPEN_FACTOR);
        int g = Math.round(((color >> 8) & 0xFF) * DEEPEN_FACTOR);
        int b = Math.round((color & 0xFF) * DEEPEN_FACTOR);
        return alpha | (r << 16) | (g << 8) | b;
    }
}
