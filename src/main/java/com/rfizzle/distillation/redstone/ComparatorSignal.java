package com.rfizzle.distillation.redstone;

/**
 * The pure brew-state comparator scale ({@code design/SPEC.md} §9). Vanilla's brewing stand emits a
 * generic container-fullness signal that can't tell a stand mid-cycle from a finished one; this maps
 * the stand's state onto two readable bands instead, keyed only on whether a cycle is running and how
 * many bottle slots are occupied — no {@code net.minecraft.*} types, so it stays plain-JUnit testable.
 *
 * <table>
 *   <tr><th>State</th><th>Signal</th></tr>
 *   <tr><td>idle, no bottles</td><td>{@code 0}</td></tr>
 *   <tr><td>brewing</td><td>{@code 1..6} — the bottle count (working band)</td></tr>
 *   <tr><td>idle, bottles present</td><td>{@code 8..13} — {@code count + 7} (done band)</td></tr>
 * </table>
 *
 * <p>So {@code done ⇔ signal >= }{@link #DONE_BASE}, and the count is {@code signal} while working or
 * {@code signal - 7} when done. The bands never overlap: the working band tops out at
 * {@link #WORKING_MAX} (6, the rigged six-bottle batch) and the done band starts at 8, leaving 7 as an
 * unused gap so a single threshold cleanly separates "cooking" from "ready".
 */
public final class ComparatorSignal {

    /** Highest working-band value — a full rigged batch of six bottles mid-cycle. */
    public static final int WORKING_MAX = 6;

    /** Lowest done-band value — idle with a single bottle. {@code signal >= DONE_BASE} means done. */
    public static final int DONE_BASE = 8;

    private ComparatorSignal() {
    }

    /**
     * Maps brew state to the 0–13 comparator scale.
     *
     * @param brewing     a brew cycle is in flight (normal or batch)
     * @param bottleCount occupied bottle slots (0–2, plus the batch row 5–7 when rigged); clamped to
     *                    {@code 0..}{@link #WORKING_MAX} so a malformed count can never leave the range
     * @return the analog signal: {@code 0} when empty, {@code 1..6} while brewing, {@code 8..13} when
     *     idle with bottles present
     */
    public static int of(boolean brewing, int bottleCount) {
        int count = Math.max(0, Math.min(bottleCount, WORKING_MAX));
        if (count == 0) {
            return 0;
        }
        return brewing ? count : DONE_BASE - 1 + count;
    }
}
