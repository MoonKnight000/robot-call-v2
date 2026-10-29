package uz.murodjon.robotcallv2.scenario.domain.enums;

/**
 * What a stage of the flow does when the call enters it — the node palette of the visual
 * editor. Only {@link #CONVERSATION} talks with the caller turn by turn; every other type
 * does one thing and hands the call on (or ends it), which is why its settings live in
 * {@code StageDef.action} rather than in a prompt.
 */
public enum StageType {
    /** The model talks with the caller on the stage's purpose until a transition is taken. */
    CONVERSATION,
    /** Calls one tool ({@code action.toolName}) and moves on with its result. */
    FUNCTION,
    /** Hands the call to a phone number ({@code action.transferTo}); a transition, if any, is taken when it fails. */
    CALL_TRANSFER,
    /** Sends DTMF tones ({@code action.digits}) — an IVR menu on the other side. */
    PRESS_DIGIT,
    /** Picks the next stage from the call's facts by {@code branches}, without the model. */
    LOGIC_SPLIT,
    /** Hands the conversation to another AI agent ({@code action.targetAgentId}); a transition, if any, is taken when it fails. */
    AGENT_TRANSFER,
    /** Sends a text message ({@code action.smsText}) to the caller during the call. */
    SMS,
    /** Has the model read outcome values ({@code action.extractFields}) out of the conversation so far. */
    EXTRACT_VARIABLE,
    /** Runs a short script ({@code action.code}) over the call's facts. */
    CODE,
    /** Calls one tool of a company MCP connection ({@code action.mcpConnectionId}, {@code action.toolName}). */
    MCP,
    /** Says its line and hangs up. */
    ENDING,
    /** A comment on the canvas; never entered by a call. */
    NOTE;

    /**
     * Whether the stage does its one job and must then hand the call on: it cannot be where a
     * call ends, so it needs an outgoing transition.
     */
    public boolean passesCallOn() {
        return switch (this) {
            case FUNCTION, PRESS_DIGIT, LOGIC_SPLIT, SMS, EXTRACT_VARIABLE, CODE, MCP -> true;
            case CONVERSATION, CALL_TRANSFER, AGENT_TRANSFER, ENDING, NOTE -> false;
        };
    }
}
