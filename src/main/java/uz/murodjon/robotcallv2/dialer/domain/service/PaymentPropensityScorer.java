package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.callrecord.domain.entity.PhoneCallHistory;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseTally;

import java.util.Map;

/**
 * A rule-based guess at how likely a number is to pay, from what the company already knows
 * about it: whether it picks up, whether it refuses, and whether its promises were kept. No
 * history at all is {@link TargetScore#NEUTRAL}, so a fresh list keeps its import order.
 *
 * <ul>
 *   <li>reach rate, smoothed as (reached + 1) / (placed + 2) so one missed call is not a
 *       verdict: ±{@value #REACH_WEIGHT_HALF} around 50%</li>
 *   <li>each REFUSED call: −{@value #REFUSED_PENALTY}, at most −{@value #REFUSED_CAP}</li>
 *   <li>each kept promise: +{@value #PROMISE_WEIGHT}, each broken one −{@value #PROMISE_WEIGHT},
 *       at most ±{@value #PROMISE_CAP} each way</li>
 * </ul>
 *
 * The best hour is the company-local hour with the most reached calls; on a tie, the earliest.
 */
public final class PaymentPropensityScorer {

    static final int REACH_WEIGHT_HALF = 20;
    static final int REFUSED_PENALTY = 5;
    static final int REFUSED_CAP = 15;
    static final int PROMISE_WEIGHT = 15;
    static final int PROMISE_CAP = 30;

    private PaymentPropensityScorer() {
    }

    /** Either argument may be null: no calls, or no promises, on record. */
    public static TargetScore score(PhoneCallHistory calls, PaymentPromiseTally promises) {
        PhoneCallHistory history = calls != null ? calls : PhoneCallHistory.NONE;
        PaymentPromiseTally tally = promises != null ? promises : PaymentPromiseTally.NONE;

        double score = TargetScore.NEUTRAL;
        if (history.placed() > 0) {
            double reachRate = (history.reached() + 1.0) / (history.placed() + 2.0);
            score += (reachRate - 0.5) * 2 * REACH_WEIGHT_HALF;
        }
        score -= Math.min(history.refused() * REFUSED_PENALTY, REFUSED_CAP);
        score += Math.min(tally.kept() * PROMISE_WEIGHT, PROMISE_CAP);
        score -= Math.min(tally.broken() * PROMISE_WEIGHT, PROMISE_CAP);

        int clamped = (int) Math.max(TargetScore.MIN, Math.min(TargetScore.MAX, Math.round(score)));
        return new TargetScore(clamped, findBestHour(history.reachedByHour()));
    }

    private static Integer findBestHour(Map<Integer, Integer> reachedByHour) {
        Integer best = null;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : reachedByHour.entrySet()) {
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && count > 0 && entry.getKey() < best)) {
                best = entry.getKey();
                bestCount = count;
            }
        }
        return best;
    }
}
