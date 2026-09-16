package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** How a supervisor joins someone else's live call. */
public enum MonitorMode {
    /** Hear both sides; nobody hears the supervisor. */
    LISTEN,
    /** Hear both sides; only the operator hears the supervisor (coaching). */
    WHISPER,
    /** Join the conversation; everyone hears everyone. */
    BARGE
}
