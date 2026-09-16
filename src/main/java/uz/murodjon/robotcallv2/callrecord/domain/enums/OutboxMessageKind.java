package uz.murodjon.robotcallv2.callrecord.domain.enums;

/**
 * What an {@code outbox_message} row asks to be written, and where. A call gets at most one
 * message of each kind: kind and call attempt together are the message's idempotency key.
 */
public enum OutboxMessageKind {
    /** The call's summary, as a note on the CRM lead. */
    CRM_NOTE,
    /** The conversation itself, on the lead's call history. */
    CRM_CALL_HISTORY
}
