package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentDataPolicy;
import uz.murodjon.robotcallv2.aiagent.domain.enums.RealtimeFactsDelivery;

/**
 * {@link AiAgentDataPolicy} as the agent endpoints read and write it. On a write a null
 * field keeps what the agent has (or the create default).
 */
public record AgentDataPolicyDto(
        Boolean zeroPiiRetention,
        Boolean storeCallAudio,
        Integer conversationRetentionDays,
        RealtimeFactsDelivery realtimeFactsDelivery
) {

    public static AgentDataPolicyDto of(AiAgentDataPolicy policy) {
        return new AgentDataPolicyDto(policy.zeroPiiRetention(), policy.storeCallAudio(),
                policy.conversationRetentionDays(), policy.realtimeFactsDelivery());
    }
}
