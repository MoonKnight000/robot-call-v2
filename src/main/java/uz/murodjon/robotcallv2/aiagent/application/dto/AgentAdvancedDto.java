package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;

/**
 * The "advanced" settings section. The groups are the same ones {@link AiAgentRow} carries;
 * of the voice only the two delivery switches belong here.
 */
public record AgentAdvancedDto(
        Boolean useRag,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        Boolean emotionAdaptiveVoice,
        SpeakingWarmth speakingWarmth,
        AgentAmbienceDto ambience,
        @Valid AgentCallBehaviourDto callBehaviour,
        AgentDataPolicyDto dataPolicy
) {
}
