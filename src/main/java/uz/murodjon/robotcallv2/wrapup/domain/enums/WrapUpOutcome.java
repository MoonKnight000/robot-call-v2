package uz.murodjon.robotcallv2.wrapup.domain.enums;

/**
 * What an operator's wrap-up means for the client's campaign target
 * (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.1).
 */
public enum WrapUpOutcome {
    /** The call reached its purpose; the target is finished. */
    DONE,
    /** Worth another call — at the operator's {@code callbackAt}, or by the campaign's retry rules. */
    RETRY,
    /** Not worth calling again (wrong number, refused for good); the target is closed unreached. */
    EXHAUSTED
}
