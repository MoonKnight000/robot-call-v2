package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;

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
        String integrationTool
) {

    public AgentFunction {
        description = description == null || description.isBlank() ? null : description.trim();
        executionMessage = executionMessage == null || executionMessage.isBlank() ? null : executionMessage.trim();
        integrationTool = integrationTool == null || integrationTool.isBlank() ? null : integrationTool.trim();
    }

    public static AgentFunctionBuilder builder() {
        return new AgentFunctionBuilder();
    }

    public AgentFunctionBuilder toBuilder() {
        return new AgentFunctionBuilder(this);
    }
}
