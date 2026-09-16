package uz.murodjon.robotcallv2.insight.domain.enums;

/**
 * How far a proposal has got. Nothing reaches a call from here without a person:
 * {@link #ACCEPTED} is as far as the loop goes on its own, and publishing stays a button
 * somebody presses (UNIVERSAL-OPERATOR-PLAN D.4).
 */
public enum InsightStatus {

    /** Waiting in the inbox. */
    PROPOSED,

    /** Accepted, the change is in the draft, and its evaluation run is deciding whether it stays. */
    EVALUATING,

    /** Accepted: applied to the draft where there was something to apply, acknowledged where there was not. */
    ACCEPTED,

    /** Turned down — by a person, or by the evaluation the accepted change then failed. */
    REJECTED
}
