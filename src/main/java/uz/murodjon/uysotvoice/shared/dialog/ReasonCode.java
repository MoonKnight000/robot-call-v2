package uz.murodjon.uysotvoice.shared.dialog;

/**
 * Reason a debt is unpaid, recorded via the {@code recordRefusalReason} tool.
 * See PROJECT.md §4.2.
 */
public enum ReasonCode {
    NO_MONEY,         // no money
    JOB_LOSS,         // lost job
    ILLNESS,          // illness
    ALREADY_PAID,     // "I already paid" — must be verified
    DISPUTES_DEBT,    // does not acknowledge the debt
    FORGOT,           // forgot
    TECHNICAL_ISSUE,  // payment did not go through
    OTHER
}
