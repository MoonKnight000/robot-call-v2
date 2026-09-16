package uz.murodjon.robotcallv2.agent.queue;

/** How a call's time in a queue — or ringing a group, an extension, a number — ended. */
public enum QueueOutcome {
    /** An operator talked to the caller and has hung up; the caller is still there. */
    ANSWERED,
    /** Waited too long, the queue was full, or nobody was signed in. */
    OVERFLOW,
    /** The caller pressed 1 to be called back instead of waiting. */
    CALLBACK,
    /** The caller hung up while waiting. */
    ABANDONED,
    /** The agent that kept the caller company while waiting sorted it out. */
    RESOLVED_BY_AGENT,
    /** Rang out with nobody picking up (ring group, extension, forwarded number). */
    NO_ANSWER
}
