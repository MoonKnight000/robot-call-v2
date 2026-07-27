package uz.murodjon.uysotvoice.shared.dialog;

/**
 * Final outcome of a call attempt. See PROJECT.md §4.2 and §8.6.
 */
public enum Disposition {
    PROMISE_TO_PAY,   // promise to pay obtained
    REFUSED,          // refused
    NO_ANSWER,        // no answer
    WRONG_NUMBER,     // wrong number
    DO_NOT_CALL,      // client asked not to be called again (§11.4)
    HUNG_UP,          // hung up
    TRANSFERRED,      // transferred to a human operator
    VOICEMAIL,        // answering machine
    FAILED            // technical failure
}
