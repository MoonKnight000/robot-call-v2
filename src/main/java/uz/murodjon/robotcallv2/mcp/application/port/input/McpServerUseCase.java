package uz.murodjon.robotcallv2.mcp.application.port.input;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.List;
import java.util.Optional;

/**
 * The platform's own MCP server (Streamable HTTP, stateless): Claude Code, Cursor or any MCP
 * client drives agents, evaluations and call history through it with an API key. Not to be
 * confused with {@link McpUseCase}, the agent's connections to other people's MCP servers.
 *
 * <p>The panel's copilot uses the same tools in-process ({@link #findServerTools},
 * {@link #callServerToolAsDraftOnly}).
 */
public interface McpServerUseCase {

    /**
     * Answers one JSON-RPC message.
     *
     * @param body the raw request body
     * @return the JSON-RPC response; empty for a notification or a client's response, which
     *         the transport acknowledges with no body
     */
    Optional<String> handleMessage(long companyId, String body);

    /** The tools the current caller holds a permission for, as {@code tools/list} would list them. */
    List<McpTool> findServerTools();

    /**
     * Runs one tool with the caller treated as drafts-only whatever its permissions: the agents
     * it creates start switched off and it never publishes or switches anything on.
     *
     * @param argumentsJson the tool's arguments as a JSON object
     * @return the tool's answer as JSON
     * @throws uz.murodjon.robotcallv2.shared.exception.AppException when the tool refuses — no
     *         permission, bad arguments, not found
     */
    String callServerToolAsDraftOnly(long companyId, String name, String argumentsJson);
}
