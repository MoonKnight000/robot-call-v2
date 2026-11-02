package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.shared.dialog.InterruptionSensitivity;
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
        Long preConversationAudioFileId,
        /** Auto-transfer to operator when sentiment drops to FRUSTRATED */
        Boolean autoEscalateOnFrustration,
        /** How many consecutive FRUSTRATED turns before auto-escalation triggers (default 2) */
        Integer frustrationThreshold,
        /** Caller silence (ms) the agent waits before its turn; 100-3000 */
        Integer responseWaitMs,
        /** Caller silence (s) before the agent asks whether they are still there; 1-300 */
        Integer reminderAfterSeconds,
        /** Reminders before the call is ended; 0-10 */
        Integer reminderMaxCount,
        /** Pause (ms) between keypad digits that hands the typed sequence over; 500-10000 */
        Integer dtmfTimeoutMs,
        /** Key that hands the sequence over at once: one of 0-9, # or * */
        String dtmfTerminationKey,
        /** Digits after which the sequence is handed over; 1-50 */
        Integer dtmfDigitLimit,
        /** Seconds an outbound call rings before it counts as unanswered; 5-300 */
        Integer ringDurationSeconds,
        /** Whether the agent may press keypad digits on the line itself (IVR navigation); not the caller typing digits */
        Boolean pressDigitEnabled,
        /** Whether the early reply's model also judges when the caller has finished; null on read = deployment default */
        Boolean llmTurnDetection
) {

    /** The shape before {@code llmTurnDetection}: it stays as the agent has it. */
    public AgentCallBehaviourDto(InterruptionSensitivity interruptionSensitivity, Boolean dtmfInputEnabled,
                                 VoicemailAction voicemailAction, String voicemailMessage, Boolean midCallSmsEnabled,
                                 String midCallSmsTemplate, String transferPhoneNumber, String transferMessage,
                                 Long transferQueueId, Long preConversationAudioFileId,
                                 Boolean autoEscalateOnFrustration, Integer frustrationThreshold,
                                 Integer responseWaitMs, Integer reminderAfterSeconds, Integer reminderMaxCount,
                                 Integer dtmfTimeoutMs, String dtmfTerminationKey, Integer dtmfDigitLimit,
                                 Integer ringDurationSeconds, Boolean pressDigitEnabled) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage, midCallSmsEnabled,
                midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, preConversationAudioFileId,
                autoEscalateOnFrustration, frustrationThreshold, responseWaitMs, reminderAfterSeconds, reminderMaxCount,
                dtmfTimeoutMs, dtmfTerminationKey, dtmfDigitLimit, ringDurationSeconds, pressDigitEnabled, null);
    }

    /** The shape before the reminder, DTMF and ring-duration fields: they stay as the agent has them. */
    public AgentCallBehaviourDto(InterruptionSensitivity interruptionSensitivity, Boolean dtmfInputEnabled,
                                 VoicemailAction voicemailAction, String voicemailMessage, Boolean midCallSmsEnabled,
                                 String midCallSmsTemplate, String transferPhoneNumber, String transferMessage,
                                 Long transferQueueId, Long preConversationAudioFileId,
                                 Boolean autoEscalateOnFrustration, Integer frustrationThreshold,
                                 Integer responseWaitMs) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage, midCallSmsEnabled,
                midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, preConversationAudioFileId,
                autoEscalateOnFrustration, frustrationThreshold, responseWaitMs, null, null, null, null, null, null, null, null);
    }

    public static AgentCallBehaviourDto of(AiAgentCallBehaviour behaviour) {
        return new AgentCallBehaviourDto(behaviour.interruptionSensitivity(), behaviour.dtmfInputEnabled(), behaviour.voicemailAction(), behaviour.voicemailMessage(),
                behaviour.midCallSmsEnabled(), behaviour.midCallSmsTemplate(), behaviour.transferPhoneNumber(),
                behaviour.transferMessage(), behaviour.transferQueueId(), behaviour.preConversationAudioFileId(),
                behaviour.autoEscalateOnFrustration(), behaviour.frustrationThreshold(), behaviour.responseWaitMs(),
                behaviour.reminderAfterSeconds(), behaviour.reminderMaxCount(), behaviour.dtmfTimeoutMs(),
                behaviour.dtmfTerminationKey(), behaviour.dtmfDigitLimit(), behaviour.ringDurationSeconds(),
                behaviour.pressDigitEnabled(), behaviour.llmTurnDetection());
    }
}
