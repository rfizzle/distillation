package com.rfizzle.distillation.brew;

/**
 * The pure arithmetic of the splash &amp; lingering rebalance ({@code design/SPEC.md} §7), extracted
 * from {@link com.rfizzle.distillation.mixin.ThrownPotionMixin} so the numbers are unit-testable
 * without a running server and the mixin stays a thin config-to-vanilla shell — the same split
 * {@link HonestDurations} uses for §4.
 *
 * <p>A splash applies a <em>flat</em> fraction of the drinkable duration to every duration-bearing
 * effect it lands, regardless of how far the hit entity stood from the impact — vanilla's
 * distance falloff is dropped for these effects (instant effects keep vanilla's distance scaling,
 * untouched by this class). A lingering cloud's per-tick shrink keeps vanilla's linear
 * {@code -radius / duration} formula, so a rebalanced cloud still tapers to zero exactly at the end
 * of its (longer) life.
 */
public final class ThrownRebalance {

    private ThrownRebalance() {
    }

    /**
     * The flat splash duration in ticks: {@code ⌊factor · baseTicks + 0.5⌋}. Mirrors vanilla's own
     * {@code (int)(e · duration + 0.5)} rounding (round-half-up on non-negative values), with the
     * config factor standing in for vanilla's distance term {@code e}.
     */
    public static int splashDuration(int baseTicks, float factor) {
        return (int) ((double) factor * (double) baseTicks + 0.5);
    }

    /**
     * The linear per-tick radius decrement for a lingering cloud: {@code -radius / duration}, so the
     * cloud shrinks from its full radius to zero over exactly {@code durationTicks} ticks — vanilla's
     * own formula, carried onto the rebalanced radius and lifetime. Guards a zero/negative duration
     * (never reachable through the clamped config, which floors the lifetime at 600) by returning
     * {@code 0} rather than dividing by zero.
     */
    public static float cloudRadiusPerTick(float radius, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0F;
        }
        return -radius / (float) durationTicks;
    }
}
