package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * What one MCP tool does with its arguments. Every refusal is an {@code AppException}, which the
 * server hands back to the model as the tool's error result.
 */
@FunctionalInterface
public interface McpToolHandler {

    /**
     * @param draftOnly the caller may write drafts only (the copilot always, a key with
     *                  {@code AGENT_DRAFT_EDIT} and without {@code AI_AGENT_EDIT})
     */
    Object call(long companyId, String tool, ObjectNode arguments, boolean draftOnly);
}
