package uz.murodjon.robotcallv2.billing.domain.enums;

/** Why a row exists in the money ledger. */
public enum LedgerEntryType {

    /** A payment landed and raised the balance. */
    TOPUP,

    /** Money set aside for a call about to be dialled. Moves the hold, not the balance. */
    RESERVE,

    /** A hold given back because the call was never charged. */
    RELEASE,

    /** A finished call, charged. This is the only entry that lowers a balance by itself. */
    CALL_CHARGE,

    /** A correction somebody made by hand; the reason belongs in {@code reference_id}. */
    ADJUSTMENT,

    /** Tokens a panel AI service used — the copilot. No call is behind it. */
    AI_CHARGE,

    /** Free trial money given to a self-registered company. */
    TRIAL_CREDIT,

    /** The unspent part of a trial credit, taken back when the trial ends. */
    TRIAL_EXPIRE
}
