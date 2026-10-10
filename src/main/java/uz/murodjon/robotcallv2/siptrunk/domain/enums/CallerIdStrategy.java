package uz.murodjon.robotcallv2.siptrunk.domain.enums;

/**
 * How an outbound call picks a caller ID from the pool.
 */
public enum CallerIdStrategy {
    /** Rotates evenly through active numbers in the pool. */
    ROUND_ROBIN,
    /** Picks randomly among active numbers. */
    RANDOM,
    /** Picks the number that has been used the least. */
    LEAST_USED,
    /** Picks the number with the highest historical answer rate. */
    HIGHEST_ANSWER_RATE
}
