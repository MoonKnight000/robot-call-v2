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
}
