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
    COMPLETED            // scenario finished normally with no debt-specific outcome (ROADMAP A.3)
}
