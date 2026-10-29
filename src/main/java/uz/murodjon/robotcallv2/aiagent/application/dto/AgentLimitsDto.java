package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;

public record AgentLimitsDto(
        Integer concurrentCallsLimit,
        Integer dailyCallsLimit,
        Integer maxConversationDurationSeconds,
        Integer silenceEndCallTimeoutSeconds
) {

    public static AgentLimitsDto of(AiAgentLimits limits) {
        return new AgentLimitsDto(limits.concurrentCallsLimit(), limits.dailyCallsLimit(),
                limits.maxConversationDurationSeconds(), limits.silenceEndCallTimeoutSeconds());
    }
}
