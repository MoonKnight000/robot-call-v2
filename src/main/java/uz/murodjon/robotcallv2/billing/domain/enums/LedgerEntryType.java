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
    ADJUSTMENT
}
