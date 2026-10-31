package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * What the calls of a period did and cost — every figure counted over the same calls (those
 * started in the period), so a promise or a payment is credited to the period of the call
 * that won it, not to the day it was reported.
 *
 * @param totalCalls         calls started
 * @param answeredCalls      of them, answered
 * @param talkSeconds        time spent talking on the answered ones
 * @param spentUzs           what the company was charged for them
 * @param promises           payment promises they won
 * @param promisedAmountUzs  the sum promised
 * @param conversions        conversions (payments, sales…) the company reported and that were credited to these calls
 * @param conversionValueUzs their value — real money in, unlike a promise
 */
public record RoiTotals(
        long totalCalls,
        long answeredCalls,
        long talkSeconds,
        long spentUzs,
        long promises,
        long promisedAmountUzs,
        long conversions,
        long conversionValueUzs
) {
}
