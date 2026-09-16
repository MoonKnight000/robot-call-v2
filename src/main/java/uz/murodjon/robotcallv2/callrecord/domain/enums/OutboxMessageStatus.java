package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** Where an {@code outbox_message} row is in its delivery. */
public enum OutboxMessageStatus {
    /** Waiting for its first or next delivery attempt. */
    PENDING,
    /** The remote system accepted it; it is never sent again. */
    SENT,
    /** Every attempt failed; nothing tries it again, and an alert was raised. */
    DEAD
}
