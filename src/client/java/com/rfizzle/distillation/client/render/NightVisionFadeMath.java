package com.rfizzle.distillation.client.render;

/**
 * The smooth replacement for vanilla's Night Vision expiry flicker. Pure math, no Minecraft types,
 * so it is covered by a Tier 1 unit test without a client bootstrap.
 *
 * <p>Vanilla's {@code GameRenderer.getNightVisionScale} holds a flat {@code 1.0} until the effect
 * ends within {@link #FADE_TICKS} ticks, then oscillates its brightness scale between 0.4 and 1.0
 * every frame — the full-screen strobe. This curve keeps the flat plateau, then ramps linearly and
 * monotonically down to {@code 0.0} at expiry, so the effect dims out instead of pulsing. The ramp
 * meets the plateau continuously at {@link #FADE_TICKS}, so there is no jump where the fade begins.
 */
public final class NightVisionFadeMath {

    /** The final window over which Night Vision fades — the same span vanilla flickers across. */
    public static final int FADE_TICKS = 200;

    private NightVisionFadeMath() {
    }

    /**
     * The brightness scale (0.0–1.0) to substitute for vanilla's flicker, given the effect's
     * remaining duration and the frame's partial tick.
     *
     * @param durationTicks the effect's remaining duration in ticks
     * @param partialTick   the render frame's fractional progress into the current tick (0.0–1.0)
     * @return {@code 1.0} while more than {@link #FADE_TICKS} ticks remain, then a linear ramp to
     * {@code 0.0} at expiry, clamped to {@code [0.0, 1.0]}
     */
    public static float scale(int durationTicks, float partialTick) {
        float remaining = durationTicks - partialTick;
        if (remaining >= FADE_TICKS) {
            return 1.0F;
        }
        if (remaining <= 0.0F) {
            return 0.0F;
        }
        return remaining / FADE_TICKS;
    }
}
