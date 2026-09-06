package uz.murodjon.robotcallv2.shared.dialog;

/**
 * Final outcome of a call attempt. See PROJECT.md §4.2 and §8.6.
 */
public enum Disposition {
    PROMISE_TO_PAY,      // promise to pay obtained
    REFUSED,             // refused
    NO_ANSWER,           // rang, but the subscriber never picked up
    CARRIER_REJECTED,    // the carrier refused to route the call; it never reached the subscriber
    WRONG_NUMBER,        // wrong number
    DO_NOT_CALL,         // client asked not to be called again (§11.4)
    HUNG_UP,             // hung up
    TRANSFERRED,         // transferred to a human operator
    VOICEMAIL,           // answering machine / carrier announcement
    CALLBACK_REQUESTED,  // caller asked to be called back at a specific time
    FAILED,              // technical failure
    COMPLETED;           // scenario finished normally with no debt-specific outcome (ROADMAP A.3)

    /**
     * Whether this outcome is the one the call was placed to get — what an A/B test counts
     * as a conversion.
     *
     * <p>Two outcomes qualify. {@link #PROMISE_TO_PAY} is the point of a collection call.
     * {@link #COMPLETED} is the point of every other scenario: it means the conversation
     * reached its last stage on its own terms.
     *
     * <p>{@link #TRANSFERRED} deliberately does not. Handing the call to a person is the
     * bot failing to finish it, and counting it would rank the script that gives up
     * soonest highest.
     */
    public boolean isConversion() {
        return this == PROMISE_TO_PAY || this == COMPLETED;
    }
}
