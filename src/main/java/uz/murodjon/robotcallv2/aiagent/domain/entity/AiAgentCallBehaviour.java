package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.InterruptionSensitivity;
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
 * @param autoEscalateOnFrustration auto-transfer to operator when sentiment drops to FRUSTRATED;
 *                                null = disabled (RETELL_AI_GAP_ANALYSIS.md #1)
 * @param frustrationThreshold how many consecutive FRUSTRATED turns before auto-escalation triggers;
 *                                default 2 if null
 * @param responseWaitMs           caller silence, after the caller stops speaking, the agent waits before
 *                                taking its turn; null = deployment default. Cascade pipeline only; a
 *                                scenario stage may replace it ({@code StageOverrides.responseWaitMs})
 * @param reminderAfterSeconds   caller silence after which the agent asks whether they are still there; null = deployment default
 * @param reminderMaxCount       how many such reminders before the call is ended; null = deployment default, 0 = end at the first silence
 * @param dtmfTimeoutMs          pause between keypad digits after which the typed sequence is handed over; null = 2000
 * @param dtmfTerminationKey     key that hands the sequence over at once (0-9, # or *); null = #
 * @param dtmfDigitLimit         digits after which the sequence is handed over without waiting; null = no limit
 * @param ringDurationSeconds    how long an outbound call rings before it counts as unanswered; null = deployment default
 * @param pressDigitEnabled       whether the agent may press keypad digits on the line itself (IVR navigation, the
 *                                {@code sendDtmfTones} tool) without its scenario declaring that tool; not
 *                                {@code dtmfInputEnabled}, which is the caller typing digits
 * @param llmTurnDetection        whether the model writing the early reply also judges when the caller has
 *                                finished — it starts answering, or chooses to wait for them — and so moves the
 *                                silence the agent waits out; null = deployment default. Cascade pipeline only
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
        Long preConversationAudioFileId,
        /** Auto-transfer to operator when sentiment drops to FRUSTRATED; null = disabled */
        Boolean autoEscalateOnFrustration,
        /** How many consecutive FRUSTRATED turns before auto-escalation triggers; default 2 */
        Integer frustrationThreshold,
        Integer responseWaitMs,
        Integer reminderAfterSeconds,
        Integer reminderMaxCount,
        Integer dtmfTimeoutMs,
        String dtmfTerminationKey,
        Integer dtmfDigitLimit,
        Integer ringDurationSeconds,
        boolean pressDigitEnabled,
        Boolean llmTurnDetection
) {
    public AiAgentCallBehaviour {
        if (interruptionSensitivity == null) {
            interruptionSensitivity = InterruptionSensitivity.MEDIUM;
        }
        if (voicemailAction == null) {
            voicemailAction = VoicemailAction.LEAVE_MESSAGE;
        }
    }

    /** Before {@code llmTurnDetection}: it stays at the deployment default. */
    public AiAgentCallBehaviour(InterruptionSensitivity interruptionSensitivity,
                                boolean dtmfInputEnabled, VoicemailAction voicemailAction, String voicemailMessage,
                                boolean midCallSmsEnabled, String midCallSmsTemplate, String transferPhoneNumber,
                                String transferMessage, Long transferQueueId, Long preConversationAudioFileId,
                                Boolean autoEscalateOnFrustration, Integer frustrationThreshold, Integer responseWaitMs,
                                Integer reminderAfterSeconds, Integer reminderMaxCount, Integer dtmfTimeoutMs,
                                String dtmfTerminationKey, Integer dtmfDigitLimit, Integer ringDurationSeconds,
                                boolean pressDigitEnabled) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage, midCallSmsEnabled,
                midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, preConversationAudioFileId,
                autoEscalateOnFrustration, frustrationThreshold, responseWaitMs, reminderAfterSeconds, reminderMaxCount,
                dtmfTimeoutMs, dtmfTerminationKey, dtmfDigitLimit, ringDurationSeconds, pressDigitEnabled, null);
    }

    /** Before the reminder, DTMF and ring-duration settings: every one of them stays at the deployment default. */
    public AiAgentCallBehaviour(InterruptionSensitivity interruptionSensitivity,
                                boolean dtmfInputEnabled, VoicemailAction voicemailAction, String voicemailMessage,
                                boolean midCallSmsEnabled, String midCallSmsTemplate, String transferPhoneNumber,
                                String transferMessage, Long transferQueueId, Long preConversationAudioFileId,
                                Boolean autoEscalateOnFrustration, Integer frustrationThreshold, Integer responseWaitMs) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage, midCallSmsEnabled,
                midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, preConversationAudioFileId,
                autoEscalateOnFrustration, frustrationThreshold, responseWaitMs, null, null, null, null, null, null, false);
    }

    public AiAgentCallBehaviour(InterruptionSensitivity interruptionSensitivity,
                                boolean dtmfInputEnabled, VoicemailAction voicemailAction, String voicemailMessage,
                                boolean midCallSmsEnabled, String midCallSmsTemplate, String transferPhoneNumber,
                                String transferMessage, Long transferQueueId) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage,
                midCallSmsEnabled, midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId, null, null, null, null);
    }

    public AiAgentCallBehaviour(InterruptionSensitivity interruptionSensitivity,
                                boolean dtmfInputEnabled, VoicemailAction voicemailAction, String voicemailMessage,
                                boolean midCallSmsEnabled, String midCallSmsTemplate, String transferPhoneNumber,
                                String transferMessage, Long transferQueueId, Long preConversationAudioFileId) {
        this(interruptionSensitivity, dtmfInputEnabled, voicemailAction, voicemailMessage,
                midCallSmsEnabled, midCallSmsTemplate, transferPhoneNumber, transferMessage, transferQueueId,
                preConversationAudioFileId, null, null, null);
    }

    /** Whether this agent can hand the caller to a person at all. */
    public boolean transferEnabled() {
        return transferQueueId != null || (transferPhoneNumber != null && !transferPhoneNumber.isBlank());
    }
}
