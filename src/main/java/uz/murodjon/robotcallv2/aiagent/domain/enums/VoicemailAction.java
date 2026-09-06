package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Action taken when Answering Machine Detection (AMD) classifies the answered call as voicemail.
 */
public enum VoicemailAction {
    HANGUP,
    LEAVE_MESSAGE,
    IGNORE
}
