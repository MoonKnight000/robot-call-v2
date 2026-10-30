package uz.murodjon.robotcallv2.integration.domain.enums;

/**
 * Whom a changed tool default reaches. A company's own choice is never overwritten by either.
 */
public enum ToolPropagation {
    /** Every company that did not choose for itself follows the new default. */
    ALL,
    /** Companies that exist now keep what they have today; only companies created later get the new default. */
    NEW_ONLY
}
