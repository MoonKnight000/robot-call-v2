package uz.murodjon.robotcallv2.inbound.domain.enums;

/**
 * Action to take when an inbound call arrives outside of configured business hours.
 */
public enum InboundAfterHoursAction {
    /** Route to after-hours Night AI Agent. */
    AI_AGENT,
    /** Allow caller to record a voicemail message. */
    VOICEMAIL,
    /** Play custom fallback announcement text via TTS and hang up. */
    PLAY_MESSAGE_AND_HANGUP,
    /** Forward to on-duty night engineer or mobile number. */
    FORWARD_EXTERNAL
}
