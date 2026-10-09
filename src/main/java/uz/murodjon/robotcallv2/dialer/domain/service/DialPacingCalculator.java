package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.dialer.domain.entity.DialPacingStats;

/**
 * How many numbers a PREDICTIVE campaign dials this tick (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.4).
 *
 * <p>The pace is one call per free operator divided by the answer rate — a list where one call
 * in three is answered dials three per operator — capped at {@link #MAX_RATIO}. What holds it
 * back is the AI overflow: every answered client no operator is left for is taken by the AI
 * agent, and once that share reaches the campaign's ceiling the dialer drops to one call per
 * free operator. From three quarters of the ceiling on, the extra is halved, so the pace eases
 * off before it hits the wall instead of swinging between the two.
 */
public final class DialPacingCalculator {

    /** Fewer calls than this in the window say nothing about the answer rate: one per operator. */
    public static final int MIN_SAMPLES = 20;
    public static final double MAX_RATIO = 3.0;

    private DialPacingCalculator() {
    }

    /** Calls placed per free operator. */
    public static double calculateRatio(DialPacingStats stats, int maxAiOverflowPercent) {
        if (stats.dialled() < MIN_SAMPLES || stats.answered() == 0) {
            return 1.0;
        }
        double ratio = Math.min(MAX_RATIO, (double) stats.dialled() / stats.answered());
        double overflowPercent = stats.aiOverflowed() * 100.0 / stats.answered();
        if (overflowPercent >= maxAiOverflowPercent) {
            return 1.0;
        }
        if (overflowPercent >= 0.75 * maxAiOverflowPercent) {
            return 1.0 + (ratio - 1.0) / 2;
        }
        return ratio;
    }

    /**
     * New calls to place: the free operators times the ratio, less the clients already ringing
     * for them. Never negative; nothing at all without a free operator.
     */
    public static int countCallsToPlace(int freeOperators, int ringing, DialPacingStats stats,
                                        int maxAiOverflowPercent) {
        if (freeOperators <= 0) {
            return 0;
        }
        int wanted = (int) Math.floor(freeOperators * calculateRatio(stats, maxAiOverflowPercent));
        return Math.max(0, wanted - ringing);
    }
}
