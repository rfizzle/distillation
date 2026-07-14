package com.rfizzle.distillation.item;

/**
 * The pure gate for filling a flask ({@code design/SPEC.md} §12) — whether a poured or batch-brewed
 * brew may enter a flask, isolated from {@code net.minecraft.*} so it stays fast-JUnit testable. The
 * shells ({@link FlaskItem} pour, {@code BrewSeam} batch fill) resolve the brew, the flask's state,
 * and the pourer's discovery and feed the plain results here.
 */
public final class FlaskFill {

    private FlaskFill() {
    }

    /**
     * Whether the pourer has learned the brew — the discovery gate ({@code design/SPEC.md} §12,
     * mirroring the tipped-arrow charge of §8 and {@code BatchBrew.ownerMayBrew}). With discovery off
     * every brew counts as known; with it on, the brew must be produced by at least one conversion the
     * pourer has discovered — a brew no conversion produces (a foreign or base potion) fails closed.
     */
    public static boolean discoveredProducer(boolean discoveryEnabled, boolean discoveredAnyProducer,
                                             int producingConversions) {
        if (!discoveryEnabled) {
            return true;
        }
        return producingConversions > 0 && discoveredAnyProducer;
    }

    /**
     * Whether a brew may be poured into the flask: the feature is on, the flask has room, the brew is
     * compatible (the flask is empty, or already holds this exact brew — one brew per flask), and the
     * pourer has discovered a producer. A full or brew-mismatched flask fails so the shell falls
     * through to a normal drink instead.
     */
    public static boolean pourAllowed(boolean flaskEnabled, boolean flaskFull, boolean brewCompatible,
                                      boolean discoveredProducer) {
        return flaskEnabled && !flaskFull && brewCompatible && discoveredProducer;
    }
}
