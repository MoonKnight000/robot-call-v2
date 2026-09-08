package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.mcp.application.port.input.McpUseCase;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the tools a company's MCP servers offer into callbacks this turn can hand the
 * model — the same job {@link ScenarioToolCallbackFactory} does for a scenario's own tool
 * declarations and {@link HttpToolExecutor} does for a company's REST tools.
 *
 * <p>Two things are settled here rather than left to the caller. The tool list is read
 * from the connection row, not from the server: discovery happened when the connection was
 * saved, and asking somebody else's service for its tool list between the caller answering
 * and the agent speaking is not a wait this platform gets to spend. And a tool that failed
 * comes back as a sentence rather than an exception — the model has to keep the
 * conversation going without that answer, and it cannot do that with a stack trace.
 */
@Component
public class McpToolCallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallbackFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What a tool is called for the model: the connection and the tool, so two servers may share a name. */
    private static final String NAME_SEPARATOR = "__";

    private final McpUseCase mcpUseCase;

    public McpToolCallbackFactory(McpUseCase mcpUseCase) {
        this.mcpUseCase = mcpUseCase;
    }

    /**
     * Every MCP tool this call's agent may use.
     *
     * <p>Returns empty — never throws — for a company with no connections, an agent with
     * none attached, or a lookup that failed: an MCP server is an extra, and a call that
     * cannot reach one is still a call.
     */
    public List<ToolCallback> build(DialogSession session) {
        if (session.agent() == null) {
            return List.of();
        }
        try {
            Map<McpConnection, List<McpTool>> byConnection =
                    mcpUseCase.findAgentTools(session.companyId(), session.agent().id());
            List<ToolCallback> callbacks = new ArrayList<>();
            byConnection.forEach((connection, tools) ->
                    tools.forEach(tool -> callbacks.add(toCallback(connection, tool, session))));
            return callbacks;
        } catch (Exception e) {
            log.warn("[{}] MCP tools unavailable: {}", session.channelId(), e.getMessage());
            return List.of();
        }
    }

    private ToolCallback toCallback(McpConnection connection, McpTool tool, DialogSession session) {
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolName(connection, tool),
                        arguments -> mcpUseCase.callTool(session.companyId(), connection.id(),
                                session.callAttemptId(), tool.name(), arguments))
                .description(description(connection, tool))
                .inputSchema(schemaOf(tool))
                .inputType(Map.class)
                .build();
    }

    /**
     * {@code <connection>__<tool>}, sanitised the same way a company's REST tools are.
     *
     * <p>Prefixed because two servers may well both offer {@code search}, and a model
     * handed two tools of one name will call whichever it feels like.
     */
    private static String toolName(McpConnection connection, McpTool tool) {
        return HttpToolExecutor.sanitizeToolName(connection.name() + NAME_SEPARATOR + tool.name());
    }

    /** The server's own description, with its name in front so the model can tell them apart. */
    private static String description(McpConnection connection, McpTool tool) {
        String described = tool.description() == null || tool.description().isBlank()
                ? tool.name()
                : tool.description();
        return connection.name() + ": " + described;
    }

    /**
     * The server's JSON Schema, or an empty object when it sent none — a tool with no
     * declared parameters still has to be callable.
     */
    private static String schemaOf(McpTool tool) {
        if (tool.inputSchema() == null || tool.inputSchema().isBlank()) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        try {
            MAPPER.readTree(tool.inputSchema());
            return tool.inputSchema();
        } catch (Exception e) {
            log.warn("MCP tool {} has an unreadable input schema, offering it with none: {}",
                    tool.name(), e.getMessage());
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }
}
