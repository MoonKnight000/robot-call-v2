package uz.murodjon.robotcallv2.mcp.application.port.output;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.List;
import java.util.Map;

/**
 * Talking to somebody else's MCP server.
 *
 * <p>A port rather than a client held by the service, because what the service needs is
 * two questions — "what can you do" and "do this" — and everything about how that reaches
 * the far end is the adapter's: the transport, the timeouts, the screening of the address.
 */
public interface McpClientPort {

    /**
     * Asks the server what it has.
     *
     * @param bearerToken the resolved credential, or null for an unauthenticated server
     * @throws uz.murodjon.robotcallv2.shared.exception.ExternalServiceException when the
     *         server cannot be reached or does not answer like an MCP server
     */
    List<McpTool> listTools(McpConnection connection, String bearerToken);

    /**
     * Runs one tool and returns whatever text the server answered with.
     *
     * <p>Text, not a parsed structure: it goes to the model, which reads prose as happily
     * as JSON, and parsing somebody else's answer here would only add a way to fail.
     */
    String callTool(McpConnection connection, String bearerToken, String toolName, Map<String, Object> arguments);
}
