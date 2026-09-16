package uz.murodjon.robotcallv2.missedcall.domain.enums;

/** Why a caller was not served. */
public enum MissedCallReason {
    /** Rang outside working hours and nobody took the call. */
    AFTER_HOURS,
    /** Hung up while waiting in a queue. */
    ABANDONED,
    /** The queue overflowed or had nobody signed in. */
    NO_OPERATOR,
    /** Asked to be called back instead of waiting. */
    CALLBACK_REQUESTED,
    /** Left a voicemail — a request to be called back in the caller's own words. */
    VOICEMAIL
}
