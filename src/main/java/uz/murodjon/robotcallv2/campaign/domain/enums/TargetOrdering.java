package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * Which of a campaign's due targets the dialer claims first. It matters only when the campaign
 * cannot dial everyone at once — a capped or busy campaign — since otherwise every due target
 * is dialled in the same tick anyway.
 */
public enum TargetOrdering {
    /** Oldest due first, by when it became due, then by id. */
    FIFO,
    /** Highest {@code score} first, with a bonus for a target whose usual answering hour is now. */
    SCORE
}
