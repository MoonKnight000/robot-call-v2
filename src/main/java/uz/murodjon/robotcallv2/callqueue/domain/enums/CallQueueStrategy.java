package uz.murodjon.robotcallv2.callqueue.domain.enums;

/** Which available operator a waiting call is offered to first. */
public enum CallQueueStrategy {
    /** Every available member rings at once; the first to answer takes it. */
    RING_ALL,
    /** Members in turn, continuing after whoever was offered the last call. */
    ROUND_ROBIN,
    /** The member who has been free the longest. */
    LEAST_RECENT,
    /** The member who has answered the fewest calls today. */
    FEWEST_CALLS,
    RANDOM,
    /** Members holding the caller's language first; anyone after {@code 30} seconds of waiting. */
    SKILL_BASED
}
