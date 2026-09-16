package uz.murodjon.robotcallv2.quality.domain.enums;

/**
 * Where a call stands in the review inbox (UNIVERSAL-OPERATOR-PLAN D.2). The two verdicts are
 * about the agent, which is also a verdict on the judge: a flagged call marked
 * {@code AGENT_CORRECT} was a false alarm — what the judge is calibrated against.
 */
public enum CallReviewStatus {
    /** Flagged by the judge, not looked at yet. */
    PENDING,
    AGENT_CORRECT,
    AGENT_WRONG
}
