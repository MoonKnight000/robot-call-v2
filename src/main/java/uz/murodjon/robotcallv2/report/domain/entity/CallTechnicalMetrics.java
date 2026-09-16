package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * "Texnik" tab (§10.5): channel/trunk, AMD result, STT/TTS/LLM identity, token usage,
 * turn-latency stats and inbound audio quality for one call. {@code null} on
 * {@link CallReport#technical()} for calls that predate this table.
 *
 * @param channelName        the caller's Asterisk channel name
 * @param trunk               PJSIP endpoint dialled
 * @param amdResult           {@code MACHINE}/{@code HUMAN}, or null if AMD was disabled
 * @param sttProvider         speech-to-text provider used
 * @param ttsProvider         text-to-speech provider used
 * @param ttsVoice            provider-side voice name used
 * @param llmModel            LLM model used
 * @param promptTokens        prompt tokens spent, summed across the call
 * @param completionTokens    completion tokens spent (includes Gemini's thinking tokens)
 * @param cachedTokens        of {@code promptTokens}, how many were billed at the cached rate
 * @param turnCount           number of LLM turns
 * @param avgTurnLatencyMs    average client-stopped-talking -> first-audio-queued latency
 * @param maxTurnLatencyMs    the slowest turn
 * @param avgLlmLatencyMs     average LLM-only wall time per turn
 * @param maxLlmLatencyMs     the slowest one
 * @param rtpPacketsReceived  inbound RTP packets that arrived; null for calls before V11
 * @param rtpPacketsLost      inbound RTP packets that never did
 * @param rtpJitterMs         smoothed inbound jitter
 * @param rtpMos              estimated MOS of what the caller's side sent us (1.0–4.4),
 *                            null when nothing arrived
 */
public record CallTechnicalMetrics(
        String channelName,
        String trunk,
        String amdResult,
        String sttProvider,
        String ttsProvider,
        String ttsVoice,
        String llmModel,
        Integer promptTokens,
        Integer completionTokens,
        Integer cachedTokens,
        Integer turnCount,
        Integer avgTurnLatencyMs,
        Integer maxTurnLatencyMs,
        Integer avgLlmLatencyMs,
        Integer maxLlmLatencyMs,
        Integer rtpPacketsReceived,
        Integer rtpPacketsLost,
        Integer rtpJitterMs,
        Double rtpMos) {
}
