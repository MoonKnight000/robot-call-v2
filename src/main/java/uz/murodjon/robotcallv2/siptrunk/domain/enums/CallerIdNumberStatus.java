package uz.murodjon.robotcallv2.siptrunk.domain.enums;

/**
 * Operational state of a number within a Caller ID pool.
 */
public enum CallerIdNumberStatus {
    /** Eligible to be used for outbound dialing. */
    ACTIVE,
    /** Temporarily benched because of low answer rate or suspected spam flagging. */
    COOLING_DOWN,
    /** Manually disabled by operator. */
    DISABLED
}
