package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a chained target source's step decides whether to keep a row.
 *
 * <p>Deliberately small. This is the rule that stops the chain from fetching a phone
 * number for every client a company has — "only the ones more than N days late", "only the
 * ones that still owe something" — not a query language.
 */
public enum StepFilterOperator {

    /** Numeric comparisons. A row whose value is not a number never matches. */
    GT, GTE, LT, LTE,

    /** Text comparison, case-insensitive; numbers are compared as text too. */
    EQ, NE,

    /** The path resolves to something that is neither missing nor null nor blank. */
    PRESENT,

    /** The inverse of {@link #PRESENT}. */
    ABSENT
}
