package uz.murodjon.robotcallv2.operator.domain.enums;

/**
 * SSE {@code event:} names on the operator panel stream ({@code GET /api/operators/me/events})
 * and the queue wallboard stream ({@code GET /api/call-queues/live/stream}).
 */
public enum ContactCenterEventType {
    /** A call is ringing this operator; payload carries who, why and the transcript so far. */
    CALL_OFFER,
    /** The offered call was answered by this operator and is now bridged. */
    CALL_CONNECTED,
    /** The offer was withdrawn (answered elsewhere, caller left) or the call ended. */
    CALL_ENDED,
    /** This operator's presence changed. */
    STATUS_CHANGED,
    /** Wallboard tick: every queue's waiting count, longest wait and operators by status. */
    QUEUE_STATS,
    /** A missed or abandoned call joined the company's work list. */
    MISSED_CALL,
    /** A caller left a voicemail. */
    VOICEMAIL
}
