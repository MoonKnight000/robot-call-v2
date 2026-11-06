package uz.murodjon.robotcallv2.contact.domain.enums;

/** What importing one CRM record did to the contacts. */
public enum ContactImportOutcome {
    CREATED,
    UPDATED,
    /** The record matched a contact that already held the same values. */
    UNCHANGED,
    /** The record had no usable phone number. */
    SKIPPED
}
