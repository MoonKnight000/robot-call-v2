package uz.murodjon.robotcallv2.promise.domain.enums;

/** Where a promise to pay stands. Only an {@link #OPEN} one moves; the other three are final. */
public enum PaymentPromiseStatus {
    /** Waiting for its date (and the grace days after it). */
    OPEN,
    /** A payment was reported for the number, or someone marked it paid. */
    KEPT,
    /** The date and the grace days passed with no payment. */
    BROKEN,
    /** Marked so by hand, or replaced by a newer promise from the same number. */
    CANCELLED
}
