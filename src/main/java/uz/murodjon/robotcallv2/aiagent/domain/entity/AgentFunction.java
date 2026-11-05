package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionTiming;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;

/**
 * One function (tool) the agent may use on a call, as in Retell's Functions list. The type decides
 * which of the type-specific groups is filled; the others are null.
 *
 * @param id                   stable key the client gave it, unique within the list
 * @param type                 what the function does
 * @param name                 the tool name the model sees: fixed for a system type, the user's for
 *                             EXTRACT_VARIABLE, the tool's own for CUSTOM, the catalog's for INTEGRATION
 * @param description          replaces the description the model reads; null means the standard one
 * @param speakDuringExecution say something while the function runs ("talk while waiting")
 * @param executionMessage     what is said then; null means the standard filler
 * @param transfer             TRANSFER_CALL settings
 * @param agentTransfer        AGENT_TRANSFER settings
 * @param pressDigit           PRESS_DIGIT settings; null means standard
 * @param sms                  SEND_SMS settings
 * @param extract              EXTRACT_VARIABLE settings
 * @param toolId               CUSTOM: the company's tool
 * @param integrationTool      INTEGRATION: the catalog tool (its {@code toolName}, else its key)
 * @param integration          INTEGRATION: the agent's own inputs and answer fields for an HTTP catalog tool;
 *                             null keeps the catalog's. A built-in tool has none to change
 * @param timing               CUSTOM and INTEGRATION: whether the turn waits for the answer; null keeps the
 *                             tool's own "wait for result"
 */
public record AgentFunction(
        String id,
        AgentFunctionType type,
        String name,
        String description,
        boolean speakDuringExecution,
        String executionMessage,
        AgentFunctionTransfer transfer,
        AgentFunctionAgentTransfer agentTransfer,
        AgentFunctionPressDigit pressDigit,
        AgentFunctionSms sms,
        AgentFunctionExtract extract,
        Long toolId,
        String integrationTool,
        ToolOverride integration,
        AgentFunctionTiming timing
) {

    public AgentFunction {
        description = description == null || description.isBlank() ? null : description.trim();
        executionMessage = executionMessage == null || executionMessage.isBlank() ? null : executionMessage.trim();
        integrationTool = integrationTool == null || integrationTool.isBlank() ? null : integrationTool.trim();
    }

    /**
     * Whether the turn leaves this function running instead of waiting for its answer. Not an {@code is…}
     * accessor on purpose: Jackson would write it into the stored list as a field of its own.
     */
    public boolean runsInBackground() {
        return timing == AgentFunctionTiming.BACKGROUND;
    }

    public static AgentFunctionBuilder builder() {
        return new AgentFunctionBuilder();
    }

    public AgentFunctionBuilder toBuilder() {
        return new AgentFunctionBuilder(this);
    }
}
