package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Which way a call went, for the greeting lines that only suit one of them. */
public enum GreetingDirection {

    /** The caller dialled the agent. */
    INBOUND,

    /** The agent dialled the customer. */
    OUTBOUND
}
