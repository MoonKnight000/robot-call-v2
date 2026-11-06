package uz.murodjon.robotcallv2.billing.application.dto;

/**
 * Usage behind the call cost lines of {@link CostReportResponse}.
 *
 * @param calls           settled calls
 * @param callSeconds     connected seconds (telephony and the platform fee are priced on these)
 * @param sttSeconds      seconds priced at the recognition rate
 * @param realtimeSeconds seconds on a speech-to-speech engine
 * @param inputTokens     prompt tokens at the full rate
 * @param outputTokens    completion tokens
 * @param cachedTokens    prompt tokens served from cache
 * @param ttsChars        characters synthesised
 */
public record CostUsageDto(
        long calls,
        long callSeconds,
        long sttSeconds,
        long realtimeSeconds,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long ttsChars
) {
}
