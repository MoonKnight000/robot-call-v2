package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;

import java.util.Comparator;
import java.util.List;

/**
 * Whether an A/B test has actually found a better script, or has merely been running for
 * a short while.
 *
 * <p>A conversion rate on its own says nothing about how much to trust it. Two answered
 * calls out of three is 67% and one variant will always be ahead of the others, so a report
 * that names a leader and stops there names a winner on the first afternoon of every
 * campaign — and the operator then rewrites the losing script for no reason.
 *
 * <p>The fix is the interval the rate is known to within. This uses the Wilson score
 * interval rather than the textbook normal approximation because the counts here are small
 * and the rates near zero: at 1 conversion in 12 the normal interval reaches below zero,
 * which is not a probability, while Wilson stays inside 0..1 and stays honest about how
 * little 12 calls tell you. A leader is only reported as a winner when its interval clears
 * the runner-up's entirely.
 *
 * <p>Spring-free by design (CLAUDE.md §4): the rule is arithmetic, and arithmetic is the
 * cheapest thing in this project to test.
 */
public final class AbTestSignificance {

    /**
     * 95% two-sided. The z for 97.5%, which is the constant every A/B calculator uses;
     * lowering it would declare winners sooner and be wrong more often.
     */
    private static final double Z = 1.96;

    /** Below this many answered calls a variant has no interval worth comparing. */
    private static final int MIN_ANSWERED_FOR_VERDICT = 30;

    private AbTestSignificance() {
    }

    /**
     * Lower edge of the 95% Wilson interval for {@code successes / trials}.
     *
     * @return 0 when there is nothing to measure — no trials means no evidence, not a rate of zero
     */
    public static double lowerBound(int successes, int trials) {
        if (trials <= 0) {
            return 0.0;
        }
        double centre = centre(successes, trials);
        double margin = margin(successes, trials);
        return clamp(centre - margin);
    }

    /** Upper edge of the same interval. */
    public static double upperBound(int successes, int trials) {
        if (trials <= 0) {
            return 1.0;
        }
        double centre = centre(successes, trials);
        double margin = margin(successes, trials);
        return clamp(centre + margin);
    }

    /**
     * The variant that has genuinely won, or {@code null} while the test is still
     * inconclusive — which is the answer most of the time and the one worth reporting.
     *
     * <p>Two conditions, both required: every compared variant has at least
     * {@value #MIN_ANSWERED_FOR_VERDICT} answered calls, and the leader's interval sits
     * entirely above the runner-up's. A single active variant is not a winner either;
     * there is nothing it beat.
     */
    public static CampaignVariant findWinner(List<CampaignVariant> variants) {
        if (variants == null) {
            return null;
        }
        List<CampaignVariant> ranked = variants.stream()
                .filter(v -> v.answeredCount() >= MIN_ANSWERED_FOR_VERDICT)
                .sorted(Comparator.comparingDouble(
                        (CampaignVariant v) -> lowerBound(v.convertedCount(), v.answeredCount())).reversed())
                .toList();
        if (ranked.size() < 2) {
            return null;
        }

        CampaignVariant leader = ranked.get(0);
        CampaignVariant runnerUp = ranked.get(1);
        double leaderFloor = lowerBound(leader.convertedCount(), leader.answeredCount());
        double runnerUpCeiling = upperBound(runnerUp.convertedCount(), runnerUp.answeredCount());
        return leaderFloor > runnerUpCeiling ? leader : null;
    }

    private static double centre(int successes, int trials) {
        double p = (double) successes / trials;
        return (p + (Z * Z) / (2 * trials)) / (1 + (Z * Z) / trials);
    }

    private static double margin(int successes, int trials) {
        double p = (double) successes / trials;
        double spread = Math.sqrt((p * (1 - p) + (Z * Z) / (4.0 * trials)) / trials);
        return (Z * spread) / (1 + (Z * Z) / trials);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
