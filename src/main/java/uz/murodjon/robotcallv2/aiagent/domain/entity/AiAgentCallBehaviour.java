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
 * @param endpointingDelayMs      silence before the caller's turn is treated as finished; always positive
 * @param dtmfInputEnabled        whether keypad digits are accepted as input
 * @param voicemailAction         what to do when the call is answered by voicemail; never null,
 *                                {@link VoicemailAction#LEAVE_MESSAGE} unless said otherwise —
 *                                reaching the machine and saying nothing wastes the attempt
 * @param voicemailMessage        spoken when {@code voicemailAction} is to leave one
 * @param midCallSmsEnabled       whether the agent may send an SMS during the call
 * @param midCallSmsTemplate      the text it sends
 * @param transferPhoneNumber     where a handover to a human goes; null disables transfer
 * @param transferMessage         what the caller hears before being transferred
 */
public record AiAgentCallBehaviour(
        InterruptionSensitivity interruptionSensitivity,
        int endpointingDelayMs,
        boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        String transferPhoneNumber,
        String transferMessage
) {
    /** Long enough that a pause for breath is not an end of turn, short enough not to feel dead. */
    private static final int DEFAULT_ENDPOINTING_DELAY_MS = 700;

    public AiAgentCallBehaviour {
        if (interruptionSensitivity == null) {
            interruptionSensitivity = InterruptionSensitivity.MEDIUM;
        }
        if (voicemailAction == null) {
            voicemailAction = VoicemailAction.LEAVE_MESSAGE;
        }
        if (endpointingDelayMs <= 0) {
            endpointingDelayMs = DEFAULT_ENDPOINTING_DELAY_MS;
        }
    }

    /** Whether this agent can hand the caller to a person at all. */
    public boolean transferEnabled() {
        return transferPhoneNumber != null && !transferPhoneNumber.isBlank();
    }
}
