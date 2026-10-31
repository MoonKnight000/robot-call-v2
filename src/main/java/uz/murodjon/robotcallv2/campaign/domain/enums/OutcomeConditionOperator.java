package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a METRICS condition compares a call variable with its value. Text comparisons ignore
 * case; the ordering operators need a number on both sides and are false otherwise.
 */
public enum OutcomeConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    /** The variable is present and not blank; the condition's value is ignored. */
    IS_SET,
    /** The variable is missing or blank; the condition's value is ignored. */
    IS_NOT_SET
}
