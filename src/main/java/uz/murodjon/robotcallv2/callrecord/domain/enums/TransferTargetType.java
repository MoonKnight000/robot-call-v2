package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** Where an operator hands a call on to. */
public enum TransferTargetType {
    /** Another operator queue — the caller waits in line there. */
    QUEUE,
    /** One colleague, by user id. */
    USER,
    /** A phone number over the trunk. */
    EXTERNAL
}
