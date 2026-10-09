package uz.murodjon.robotcallv2.callrecord.domain.entity;

/**
 * Who talked how much on a client's call with an operator, measured from the two sides'
 * own audio (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.2) — no transcript, no LLM. Covers
 * the stretch the operator was on the call, not any AI part before it.
 *
 * @param analysedMs       how long that stretch was
 * @param clientTalkMs     the client speaking
 * @param operatorTalkMs   the operator speaking
 * @param overtalkMs       both at once
 * @param silenceMs        neither
 * @param longestSilenceMs the longest pause nobody filled
 */
public record CallTalkMetrics(
        long callId,
        int analysedMs,
        int clientTalkMs,
        int operatorTalkMs,
        int overtalkMs,
        int silenceMs,
        int longestSilenceMs
) {
}
