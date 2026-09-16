package uz.murodjon.robotcallv2.operator.domain.enums;

/**
 * Where an operator is right now (docs/INBOUND-PBX-PLAN.md §7.2). Only {@link #AVAILABLE}
 * is offered queue calls; an operator sets {@link #AVAILABLE}, {@link #BREAK} and
 * {@link #OFFLINE} by hand, the call path sets the rest.
 */
public enum OperatorStatus {
    OFFLINE,
    AVAILABLE,
    RINGING,
    ON_CALL,
    WRAP_UP,
    BREAK;

    /** Whether an operator may pick this status in the panel themselves. */
    public boolean selectableByOperator() {
        return this == AVAILABLE || this == BREAK || this == OFFLINE;
    }
}
