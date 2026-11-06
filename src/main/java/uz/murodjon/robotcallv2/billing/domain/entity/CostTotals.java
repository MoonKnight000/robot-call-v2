package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What a company's calls settled in one period used and cost — every line of
 * {@link CallCostBreakdown} summed, with the usage the lines were priced on.
 *
 * <p>Tokens are the ones that were priced: a realtime call's own audio tokens are billed
 * through the engine's minutes and are left out, its summary and analysis tokens are not.
 *
 * @param calls            settled calls
 * @param callSeconds      connected seconds
 * @param sttSeconds       seconds priced at the recognition rate (cascade calls and operator-side analysis)
 * @param realtimeSeconds  seconds that ran on a speech-to-speech engine
 * @param inputTokens      prompt tokens billed at the full rate
 * @param outputTokens     completion tokens
 * @param cachedTokens     prompt tokens served from the provider's cache
 * @param ttsChars         characters synthesised
 * @param llmPromptUzs     what the input tokens cost
 * @param llmCompletionUzs what the output tokens cost
 * @param llmCachedUzs     what the cached tokens cost
 * @param llmUzs           the LLM line; the three above add up to it
 * @param sttUzs           recognition
 * @param ttsUzs           synthesis
 * @param realtimeUzs      speech-to-speech engine minutes
 * @param telephonyUzs     the trunk
 * @param platformFeeUzs   the platform's own per-minute fee
 * @param totalUzs         the sum of the lines
 */
public record CostTotals(
        long calls,
        long callSeconds,
        long sttSeconds,
        long realtimeSeconds,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long ttsChars,
        long llmPromptUzs,
        long llmCompletionUzs,
        long llmCachedUzs,
        long llmUzs,
        long sttUzs,
        long ttsUzs,
        long realtimeUzs,
        long telephonyUzs,
        long platformFeeUzs,
        long totalUzs
) {
    public static final CostTotals NONE = new CostTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
