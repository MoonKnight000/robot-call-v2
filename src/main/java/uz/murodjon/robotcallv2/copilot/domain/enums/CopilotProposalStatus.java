package uz.murodjon.robotcallv2.copilot.domain.enums;

/** Where a change the copilot proposed stands. */
public enum CopilotProposalStatus {
    /** Waiting for the user to apply or reject it. */
    PENDING,
    /** The user applied it; the tool ran and its answer is the row's content. */
    APPLIED,
    /** The user turned it down; nothing ran. */
    REJECTED,
    /** The user applied it but the tool refused; the refusal is the row's content. */
    FAILED
}
