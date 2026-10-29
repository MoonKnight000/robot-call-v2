package uz.murodjon.robotcallv2.aiagent.application.mapper;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentAmbienceDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentCallBehaviourDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentDataPolicyDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentLimitsDto;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentAmbience;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentDataPolicy;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;

/**
 * The agent's setting groups written from their DTOs. Every field the DTO leaves null — or
 * the whole DTO when it is null — keeps {@code base}'s value: the agent's own on an edit,
 * the create defaults on a create.
 */
public final class AiAgentSettingsMapper {

    private AiAgentSettingsMapper() {
    }

    public static AiAgentAmbience toAiAgentAmbience(AgentAmbienceDto dto, AiAgentAmbience base) {
        if (dto == null) {
            return base;
        }
        return new AiAgentAmbience(
                dto.background() != null ? dto.background() : base.background(),
                dto.backgroundVolume() != null ? dto.backgroundVolume() : base.backgroundVolume(),
                dto.backgroundFadeInSeconds() != null ? dto.backgroundFadeInSeconds() : base.backgroundFadeInSeconds(),
                dto.thinking() != null ? dto.thinking() : base.thinking(),
                dto.thinkingVolume() != null ? dto.thinkingVolume() : base.thinkingVolume(),
                dto.noiseCancellationEnabled() != null ? dto.noiseCancellationEnabled() : base.noiseCancellationEnabled(),
                dto.noiseCancellationMode() != null ? dto.noiseCancellationMode() : base.noiseCancellationMode());
    }

    public static AiAgentCallBehaviour toAiAgentCallBehaviour(AgentCallBehaviourDto dto, AiAgentCallBehaviour base) {
        if (dto == null) {
            return base;
        }
        return new AiAgentCallBehaviour(
                dto.interruptionSensitivity() != null ? dto.interruptionSensitivity() : base.interruptionSensitivity(),
                dto.dtmfInputEnabled() != null ? dto.dtmfInputEnabled() : base.dtmfInputEnabled(),
                dto.voicemailAction() != null ? dto.voicemailAction() : base.voicemailAction(),
                dto.voicemailMessage() != null ? dto.voicemailMessage() : base.voicemailMessage(),
                dto.midCallSmsEnabled() != null ? dto.midCallSmsEnabled() : base.midCallSmsEnabled(),
                dto.midCallSmsTemplate() != null ? dto.midCallSmsTemplate() : base.midCallSmsTemplate(),
                dto.transferPhoneNumber() != null ? dto.transferPhoneNumber() : base.transferPhoneNumber(),
                dto.transferMessage() != null ? dto.transferMessage() : base.transferMessage(),
                dto.transferQueueId() != null ? dto.transferQueueId() : base.transferQueueId(),
                dto.preConversationAudioFileId() != null
                        ? dto.preConversationAudioFileId() : base.preConversationAudioFileId());
    }

    public static AiAgentLimits toAiAgentLimits(AgentLimitsDto dto, AiAgentLimits base) {
        if (dto == null) {
            return base;
        }
        return new AiAgentLimits(
                dto.maxConversationDurationSeconds() != null ? dto.maxConversationDurationSeconds() : base.maxConversationDurationSeconds(),
                dto.silenceEndCallTimeoutSeconds() != null ? dto.silenceEndCallTimeoutSeconds() : base.silenceEndCallTimeoutSeconds(),
                dto.concurrentCallsLimit() != null ? dto.concurrentCallsLimit() : base.concurrentCallsLimit(),
                dto.dailyCallsLimit() != null ? dto.dailyCallsLimit() : base.dailyCallsLimit());
    }

    public static AiAgentDataPolicy toAiAgentDataPolicy(AgentDataPolicyDto dto, AiAgentDataPolicy base) {
        if (dto == null) {
            return base;
        }
        return new AiAgentDataPolicy(
                dto.zeroPiiRetention() != null ? dto.zeroPiiRetention() : base.zeroPiiRetention(),
                dto.storeCallAudio() != null ? dto.storeCallAudio() : base.storeCallAudio(),
                dto.conversationRetentionDays() != null ? dto.conversationRetentionDays() : base.conversationRetentionDays(),
                dto.realtimeFactsDelivery() != null ? dto.realtimeFactsDelivery() : base.realtimeFactsDelivery());
    }
}
