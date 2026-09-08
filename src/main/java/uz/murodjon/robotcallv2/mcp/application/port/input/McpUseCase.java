package uz.murodjon.robotcallv2.mcp.application.port.input;

import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRequest;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRow;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.List;
import java.util.Map;

/** A company's links to the MCP servers it runs, and the tools they give its agents. */
public interface McpUseCase {

    List<McpConnectionRow> findConnections(long companyId);

    /**
     * Saves the connection and immediately asks the server what it has.
     *
     * <p>Asked now rather than on first use, so a typo in the URL is a message on the form
     * and not a tool that silently never appears on a call.
     */
    McpConnectionRow createConnection(long companyId, McpConnectionRequest request);

    McpConnectionRow updateConnection(long companyId, long id, McpConnectionRequest request);

    void deleteConnection(long companyId, long id);

    /** Re-reads a server's tool list and records what came back. */
    McpConnectionRow refreshConnection(long companyId, long id);

    void attachToAgent(long companyId, long agentId, long connectionId);

    void detachFromAgent(long companyId, long agentId, long connectionId);

    /**
     * Every tool an agent may use, per connection — what a call builds its tool callbacks
     * from.
     *
     * <p>Already filtered by {@code McpToolGuard}: a tool the model must not have is never
     * in this map, so nothing downstream has to remember to check again.
     */
    Map<McpConnection, List<McpTool>> findAgentTools(long companyId, long agentId);

    /**
     * Runs one tool on behalf of a call and returns what the server said.
     *
     * <p>Never throws into the call: a server that is down comes back as a short message
     * the model can say out loud, and the failure is in {@code mcp_tool_execution}.
     */
    String callTool(long companyId, long connectionId, Long callAttemptId, String toolName,
                    Map<String, Object> arguments);
}
