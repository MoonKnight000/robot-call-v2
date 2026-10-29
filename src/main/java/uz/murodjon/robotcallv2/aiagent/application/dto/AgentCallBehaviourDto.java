package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;

/**
 * {@link AiAgentCallBehaviour} as the agent endpoints read and write it. On a write a null
 * field keeps what the agent has (or the create default).
 */
public record AgentCallBehaviourDto(
        InterruptionSensitivity interruptionSensitivity,
        Boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        @Size(max = 500) String voicemailMessage,
        Boolean midCallSmsEnabled,
        @Size(max = 500) String midCallSmsTemplate,
        String transferPhoneNumber,
        String transferMessage,
        Long transferQueueId,
        Long preConversationAudioFileId
) {

    public static AgentCallBehaviourDto of(AiAgentCallBehaviour behaviour) {
        return new AgentCallBehaviourDto(behaviour.interruptionSensitivity(), behaviour.dtmfInputEnabled(), behaviour.voicemailAction(), behaviour.voicemailMessage(),
                behaviour.midCallSmsEnabled(), behaviour.midCallSmsTemplate(), behaviour.transferPhoneNumber(),
                behaviour.transferMessage(), behaviour.transferQueueId(), behaviour.preConversationAudioFileId());
    }
}
