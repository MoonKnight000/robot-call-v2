package uz.murodjon.uysotvoice.shared.dialog;

/**
 * Finite-state machine states for a debt-collection conversation.
 * See PROJECT.md §4.1 for the state diagram and transitions.
 */
public enum DialogState {
    GREETING,
    IDENTITY_CHECK,
    DEBT_NOTICE,
    REASON_INQUIRY,
    PAYMENT_DATE,
    CONFIRMATION,
    CLOSING,
    ESCALATE_TO_HUMAN,
    END_CALL
}
