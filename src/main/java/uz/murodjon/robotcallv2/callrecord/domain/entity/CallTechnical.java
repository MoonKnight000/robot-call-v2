package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.time.Instant;

/** Pure domain record for technical telemetry of a call. */
public record CallTechnical(
        long callId,
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
        Instant createdAt
) {

    /**
     * The providers a call actually ran on, before its conversation counters are known.
     * {@code createdAt} is the storage layer's to stamp; the counters are filled in by
     * {@link #withCounters}, and stay unset when the call left no snapshot behind.
     */
    public static CallTechnical of(long callId, String channelName, String trunk, String amdResult,
                                   String sttProvider, String ttsProvider, String ttsVoice, String llmModel) {
        return new CallTechnical(callId, channelName, trunk, amdResult, sttProvider, ttsProvider, ttsVoice, llmModel,
                null, null, null, null, null, null, null, null, null);
    }

    public CallTechnical withCounters(int promptTokens, int completionTokens, int cachedTokens, int turnCount,
                                      Integer avgTurnLatencyMs, Integer maxTurnLatencyMs,
                                      Integer avgLlmLatencyMs, Integer maxLlmLatencyMs) {
        return new CallTechnical(callId, channelName, trunk, amdResult, sttProvider, ttsProvider, ttsVoice, llmModel,
                promptTokens, completionTokens, cachedTokens, turnCount,
                avgTurnLatencyMs, maxTurnLatencyMs, avgLlmLatencyMs, maxLlmLatencyMs, createdAt);
    }
}
