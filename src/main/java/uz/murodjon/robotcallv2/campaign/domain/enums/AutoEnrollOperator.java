package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How an auto-enroll rule compares a contact field with its value; always case-insensitive.
 */
public enum AutoEnrollOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    /** TAG only: the value is a comma-separated list, and any one of those whole tags matches. */
    IS_ANY_OF
}
