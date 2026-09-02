package uz.murodjon.robotcallv2.inbound.domain.enums;

/**
 * Action to take when operators do not answer within the ring timeout.
 */
public enum InboundFailoverAction {
    /** Fall back to AI Voice Agent scenario. */
    SCENARIO,
    /** Fall back to Voicemail recording. */
    VOICEMAIL,
    /** Forward to a backup external mobile number. */
    EXTERNAL_FORWARD,
    /** Play busy tone or farewell and hang up. */
    HANGUP
}
