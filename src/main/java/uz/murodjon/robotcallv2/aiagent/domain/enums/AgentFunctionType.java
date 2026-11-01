package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * What a function of an agent's {@code functions} list does. The system types have one fixed
 * model-facing tool name each (the platform prompts rely on them); {@code EXTRACT_VARIABLE},
 * {@code CUSTOM} and {@code INTEGRATION} are named by what they stand for.
 */
public enum AgentFunctionType {

    END_CALL("endCall"),
    TRANSFER_CALL("requestHumanTransfer"),
    AGENT_TRANSFER("transferToAgent"),
    PRESS_DIGIT("sendDtmfTones"),
    SEND_SMS("sendMidCallSms"),
    EXTRACT_VARIABLE(null),
    CUSTOM(null),
    INTEGRATION(null);

    private final String systemToolName;

    AgentFunctionType(String systemToolName) {
        this.systemToolName = systemToolName;
    }

    /** The fixed tool name of a system type, or null for a type named by its configuration. */
    public String systemToolName() {
        return systemToolName;
    }

    /** Whether the type has a fixed name and may appear in a list at most once. */
    public boolean isSystem() {
        return systemToolName != null;
    }
}
