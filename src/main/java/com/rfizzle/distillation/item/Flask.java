package com.rfizzle.distillation.item;

/**
 * The pure dose arithmetic and drink classification of the flask ({@code design/SPEC.md} §12): a
 * flask holds up to {@link #MAX_DOSES} doses of a single brew, tracked internally as
 * <em>half-units</em> (a full dose is {@link #HALVES_PER_DOSE}), so a sneak-sipped half is simply an
 * odd count and needs no separate marker. No {@code net.minecraft.*} types — the {@link FlaskItem}
 * shell reads the stack, the drinker, and the config and feeds plain values here, so the logic stays
 * fast-JUnit testable and ports untouched across mappings.
 *
 * <p>The classification composes with the sip-half draughts of §4: a whole dose drinks full (or, when
 * the drinker sneaks a non-instant brew, sips half and leaves a pending half); a pending half always
 * finishes on the next drink, sneaking or not — the same {@link Draughts.DrinkKind} state machine a
 * potion draught uses (SIP_HALF → DRINK_HALF).
 */
public final class Flask {

    /** Half-units in one full dose. */
    public static final int HALVES_PER_DOSE = 2;
    /** The flask's capacity in whole doses. */
    public static final int MAX_DOSES = 3;
    /** The flask's capacity in half-units — a full flask. */
    public static final int MAX_HALVES = MAX_DOSES * HALVES_PER_DOSE;

    private Flask() {
    }

    /**
     * How a drink of the flask resolves, given its remaining half-units, whether the drinker sneaks,
     * and whether the brew has a non-instant effect (only a non-instant brew can be sipped in half,
     * mirroring §4). A pending half (an odd count) always finishes first — a quick swallow, sneaking
     * or not. A whole dose sips half when the drinker sneaks a non-instant brew, else drinks full.
     * Callers guard {@code halves > 0} before a drink starts, so an empty flask never reaches here.
     */
    public static Draughts.DrinkKind classify(int halves, boolean sneaking, boolean hasNonInstant) {
        if (isOdd(halves)) {
            return Draughts.DrinkKind.DRINK_HALF;
        }
        if (sneaking && hasNonInstant) {
            return Draughts.DrinkKind.SIP_HALF;
        }
        return Draughts.DrinkKind.FULL;
    }

    /** Half-units a drink of this kind consumes: a full dose is two, a sip or a stored half is one. */
    public static int halvesConsumed(Draughts.DrinkKind kind) {
        return kind == Draughts.DrinkKind.FULL ? HALVES_PER_DOSE : 1;
    }

    /** The remaining half-units after a drink of this kind, floored at zero. */
    public static int halvesAfter(int halves, Draughts.DrinkKind kind) {
        return Math.max(0, halves - halvesConsumed(kind));
    }

    /** Pouring a full dose in: two half-units added, capped at the flask's full capacity. */
    public static int addDoseHalves(int halves) {
        return Math.min(MAX_HALVES, Math.max(0, halves) + HALVES_PER_DOSE);
    }

    public static boolean isEmpty(int halves) {
        return halves <= 0;
    }

    public static boolean isFull(int halves) {
        return halves >= MAX_HALVES;
    }

    /** The whole-dose count for display: two half-units per dose. */
    public static int wholeDoses(int halves) {
        return Math.max(0, halves) / HALVES_PER_DOSE;
    }

    /** Whether a lone half-dose remains beyond the whole doses — the "½" on the tooltip. */
    public static boolean hasPendingHalf(int halves) {
        return isOdd(halves);
    }

    private static boolean isOdd(int halves) {
        return (halves & 1) == 1;
    }
}
