package uz.murodjon.robotcallv2.aiagent.application.dto;

public record AgentLimitsDto(
        Integer concurrentCallsLimit,
        Integer dailyCallsLimit,
        Integer maxConversationDurationSeconds,
        Integer silenceEndCallTimeoutSeconds,
        Integer turnTimeoutSeconds
) {
}
