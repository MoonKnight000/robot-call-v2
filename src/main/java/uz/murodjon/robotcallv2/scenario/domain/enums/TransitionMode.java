package uz.murodjon.robotcallv2.scenario.domain.enums;

/** Which stages the model may move a call to from the one it is in. */
public enum TransitionMode {
    /** Only the stage's own transitions — the drawn edges, nothing else. */
    RIGID,
    /**
     * Any conversation stage of the flow, the drawn edges first: a caller who answers the
     * next question before it is asked is followed there instead of being walked through
     * every stage in between.
     */
    FLEX
}
