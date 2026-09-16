package uz.murodjon.robotcallv2.callqueue.domain.enums;

/**
 * What happens to a caller the queue cannot serve — waited too long, the queue is full, or
 * nobody is signed in. Used when the queue was reached without a call flow around it (a
 * transfer from an AI agent); inside a flow the QUEUE node's {@code overflow} exit decides.
 */
public enum QueueOverflowAction {
    /** "Sizga qayta qo'ng'iroq qilamiz" — the caller joins the missed-call list. */
    CALLBACK,
    /** Leave a message in the voicemail box named by {@code overflowTarget}. */
    VOICEMAIL,
    /** Wait in the queue named by {@code overflowTarget}. */
    QUEUE,
    /** Talk to the AI agent named by {@code overflowTarget}. */
    AI_AGENT,
    /** Forward to the number in {@code overflowTarget}. */
    EXTERNAL_NUMBER,
    HANGUP
}
