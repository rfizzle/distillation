package com.rfizzle.distillation.arrow;

/**
 * The pure decision and arithmetic core of the potion-cauldron dip ({@code design/SPEC.md}
 * §Tipped arrows): how many arrows a single dip tips, and whether a held potion may charge a
 * cauldron. No {@code net.minecraft.*} types — the interaction shell
 * ({@link CauldronDipInteractions}) resolves potions, discovery, and item stacks and feeds the
 * plain results here, so this logic stays fast-JUnit testable.
 */
public final class ArrowTipping {

    private ArrowTipping() {
    }

    /**
     * How many arrows one dip tips: the per-dip rate, capped by the arrows actually held. The
     * caller only dips against a water cauldron (level ≥ 1), so a dip always has one water level to
     * spend — the water accounting is the caller's, not this arithmetic's.
     */
    public static int arrowsPerDip(int heldArrows, int configuredPerDip) {
        int perDip = Math.max(0, configuredPerDip);
        return Math.min(perDip, Math.max(0, heldArrows));
    }

    /**
     * Whether a held drinkable potion may charge a cauldron. With discovery off every brew counts
     * as known (mirrors {@code BatchBrew.ownerMayBrew}); with it on, the potion must be produced by
     * at least one conversion the charging player has discovered — a potion no conversion brews
     * (e.g. a foreign or base potion with no producing edge) fails closed.
     */
    public static boolean chargeAllowed(boolean discoveryEnabled, boolean discoveredAnyProducer,
                                        int producingConversions) {
        if (!discoveryEnabled) {
            return true;
        }
        return producingConversions > 0 && discoveredAnyProducer;
    }
}
