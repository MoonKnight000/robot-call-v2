package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Action taken when Answering Machine Detection (AMD) classifies the answered call as voicemail.
 */
public enum VoicemailAction {
    HANGUP,
    LEAVE_MESSAGE,
    /** As {@link #LEAVE_MESSAGE}, and the same text goes to the number by SMS once the call is over. */
    LEAVE_MESSAGE_AND_SMS,
    IGNORE;

    /** Whether the agent's message is spoken after the beep. */
    public boolean leavesMessage() {
        return this == LEAVE_MESSAGE || this == LEAVE_MESSAGE_AND_SMS;
    }
}
