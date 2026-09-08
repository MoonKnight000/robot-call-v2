package uz.murodjon.robotcallv2.billing.domain.enums;

/** Where one call's charge has got to. */
public enum CallBillingStatus {

    /** Money is held for a call that has been dialled but has not finished. */
    RESERVED,

    /** The call finished and was charged; {@code total_uzs} is what it cost. */
    SETTLED,

    /**
     * The hold was given back without a charge — the call never connected, so there is
     * nothing to bill even though a number was dialled.
     */
    RELEASED
}
