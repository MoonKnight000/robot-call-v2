package uz.murodjon.robotcallv2.quality.domain.enums;

/**
 * Where an operator call's scorecard stands in the review inbox (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md
 * §2.3b). A {@code CONFIRMED} one is the judge agreed with; a {@code CORRECTED} one is a miss to
 * learn from — what the judge's answers are calibrated against.
 */
public enum ScorecardReviewStatus {
    /** Flagged — a low score or an upset client — and not looked at yet. */
    PENDING,
    /** A supervisor read it and left the judge's answers as they were. */
    CONFIRMED,
    /** A supervisor changed at least one of the judge's answers. */
    CORRECTED
}
