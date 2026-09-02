package uz.murodjon.robotcallv2.inbound.domain.enums;

/**
 * Call distribution strategies across available operators in a queue.
 */
public enum QueueStrategy {
    /** Ring all available operators simultaneously. */
    RING_ALL,
    /** Distribute calls sequentially in a cycle across operators. */
    ROUND_ROBIN,
    /** Route to the operator with the fewest handled calls today. */
    FEWEST_CALLS,
    /** Route to the operator who has been idle the longest. */
    LEAST_RECENT,
    /** Choose a random available operator. */
    RANDOM
}
