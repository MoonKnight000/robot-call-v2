package uz.murodjon.robotcallv2.scenario.domain.enums;

/** How a {@code StageBranch} compares a call fact with its value. */
public enum BranchOperator {
    /** The fact is set and not blank; the value is not read. */
    EXISTS,
    /** The fact is unset or blank; the value is not read. */
    MISSING,
    EQUALS,
    NOT_EQUALS,
    GREATER,
    GREATER_OR_EQUAL,
    LESS,
    LESS_OR_EQUAL,
    /** Always holds; neither the fact nor the value is read — a logic split's "else", kept last. */
    ALWAYS;

    /** Whether the comparison needs a value to compare against. */
    public boolean needsValue() {
        return this != EXISTS && this != MISSING && this != ALWAYS;
    }

    /** Whether the comparison reads a fact at all. */
    public boolean needsFact() {
        return this != ALWAYS;
    }
}
