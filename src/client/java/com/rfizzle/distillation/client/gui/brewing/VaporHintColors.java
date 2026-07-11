package com.rfizzle.distillation.client.gui.brewing;

/**
 * Pure color math for the vapor hint of {@code design/SPEC.md} §1: blending the output-potion
 * colors of the valid conversions a held ingredient would make. Each color is a packed
 * {@code 0xRRGGBB} (potion tint, no alpha); the render opacity is applied separately by the
 * renderer. Kept Minecraft-free so the blend is unit-tested on its own.
 */
public final class VaporHintColors {
    private VaporHintColors() {
    }

    /** The 60% opacity the spec applies to the vapor tint. */
    public static final float HINT_OPACITY = 0.6F;

    /**
     * Blends distinct output colors into one tint by averaging each channel — "multiple distinct
     * outputs blend their colors" (§1). Returns {@code 0} for an empty input (no valid pair, so the
     * renderer paints nothing).
     */
    public static int blend(int[] colors) {
        if (colors.length == 0) {
            return 0;
        }
        int r = 0;
        int g = 0;
        int b = 0;
        for (int color : colors) {
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        r /= colors.length;
        g /= colors.length;
        b /= colors.length;
        return (r << 16) | (g << 8) | b;
    }

    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }
}
