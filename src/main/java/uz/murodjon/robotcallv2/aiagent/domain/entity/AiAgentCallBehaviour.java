package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;

/**
 * How the agent conducts the call itself, as opposed to what it says.
 *
 * <p>Turn-taking, what to do when a machine answers instead of a person, and the two ways
 * a call can leave the agent: an SMS mid-call, or a transfer to a human.
 *
 * @param interruptionSensitivity how readily the agent stops talking when the caller starts; never null
 * @param dtmfInputEnabled        whether keypad digits are accepted as input
 * @param voicemailAction         what to do when the call is answered by voicemail; never null,
 *                                {@link VoicemailAction#LEAVE_MESSAGE} unless said otherwise —
 *                                reaching the machine and saying nothing wastes the attempt
 * @param voicemailMessage        spoken when {@code voicemailAction} is to leave one
 * @param midCallSmsEnabled       whether the agent may send an SMS during the call
 * @param midCallSmsTemplate      the text it sends
 * @param transferPhoneNumber     where a handover to a human goes; null disables transfer
 * @param transferMessage         what the caller hears before being transferred
 * @param transferQueueId         the operator queue a handover joins; wins over
 *                                {@code transferPhoneNumber} (docs/INBOUND-PBX-PLAN.md §7.4)
 * @param preConversationAudioFileId an uploaded WAV played as soon as the call is up, before the
 *                                agent says anything — a recorded notice or a jingle; null = none
 */
public record AiAgentCallBehaviour(
        InterruptionSensitivity interruptionSensitivity,
        boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        String transferPhoneNumber,
        String transferMessage,
        Long transferQueueId,
        Long preConversationAudioFileId
) {
    public AiAgentCallBehaviour {
        if (interruptionSensitivity == null) {
            interruptionSensitivity = InterruptionSensitivity.MEDIUM;
        }
        if (voicemailAction == null) {
            voicemailAction = VoicemailAction.LEAVE_MESSAGE;
        }
    }

    public AiAgentCallBehaviour(InterruptionSensitivity interruptionSensitivity,
                                boolean dtmfInputEnabled, VoicemailAction voicemailAction, String voicemailMessage,
                                boolean midCallSmsEnabled, String midCallSmsTemplate, String transferPhoneNumber,
                                String transferMessage, Long transferQueueId) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage,
                midCallSmsEnabled, midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, null);
    }

    /** Whether this agent can hand the caller to a person at all. */
    public boolean transferEnabled() {
        return transferQueueId != null || (transferPhoneNumber != null && !transferPhoneNumber.isBlank());
    }
}
