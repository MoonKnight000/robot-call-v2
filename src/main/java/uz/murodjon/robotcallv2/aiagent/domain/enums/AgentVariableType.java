package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** The type of a variable an EXTRACT_VARIABLE function reads out of the conversation. */
public enum AgentVariableType {
    STRING,
    NUMBER,
    BOOLEAN,
    /** One of a fixed list of choices. */
    ENUM
}
