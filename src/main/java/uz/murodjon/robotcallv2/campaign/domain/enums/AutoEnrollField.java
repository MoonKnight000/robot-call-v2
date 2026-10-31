package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * The contact field an auto-enroll rule looks at.
 */
public enum AutoEnrollField {
    /** One of the contact's comma-separated tags; EQUALS matches a whole tag. */
    TAG,
    NAME,
    /** The number's digits only, so {@code +998 71 …} and {@code 99871…} read the same. */
    PHONE,
    ADDRESS,
    NOTES,
    /** Whether the contact's number is on the company's do-not-call list; EQUALS {@code true} or {@code false}. */
    DO_NOT_CALL
}
