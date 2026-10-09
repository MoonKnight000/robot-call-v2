package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One operator's scored calls over a period (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4) — a
 * line of the operator rating.
 *
 * @param averageScore              the score as it stands, a supervisor's correction included;
 *                                  null without a scored call
 * @param flaggedCalls              calls that went to the review inbox
 * @param correctedCalls            calls a supervisor scored differently from the judge
 * @param upsetClientCalls          calls whose client the judge read as NEGATIVE or HOSTILE
 * @param averageOperatorTalkPercent the operator's share of the talking, of the scored calls
 *                                  with talk metrics; null without such a call
 * @param averageOvertalkPercent    both talking at once, as a share of the call
 * @param averageLongestSilenceSeconds the longest silence of a call, averaged
 */
public record OperatorQualityScore(
        long userId,
        String name,
        long scoredCalls,
        Double averageScore,
        long flaggedCalls,
        long correctedCalls,
        long upsetClientCalls,
        Double averageOperatorTalkPercent,
        Double averageOvertalkPercent,
        Double averageLongestSilenceSeconds
) {
}
