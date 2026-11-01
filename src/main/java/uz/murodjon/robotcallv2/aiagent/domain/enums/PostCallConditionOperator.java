package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * How a {@code PostCallCondition} compares an extracted value with its own. {@link #IS_SET}
 * and {@link #IS_NOT_SET} take no value; {@link #GREATER_THAN} and {@link #LESS_THAN} compare
 * numbers and hold false when either side is not one.
 */
public enum PostCallConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN,
    IS_SET,
    IS_NOT_SET;

    public boolean needsValue() {
        return this != IS_SET && this != IS_NOT_SET;
    }

    public boolean isNumeric() {
        return this == GREATER_THAN || this == LESS_THAN;
    }
}
