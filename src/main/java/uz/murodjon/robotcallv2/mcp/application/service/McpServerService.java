package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpServerUseCase;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The JSON-RPC side of the platform's MCP server: {@code initialize}, {@code ping},
 * {@code tools/list} and {@code tools/call}; the tools themselves are {@link McpServerToolCatalog}.
 *
 * <p>Stateless: no session id, no server-to-client stream — every message carries the API key
 * and is answered on its own. A refused tool call (no permission, bad arguments, not found) is
 * the tool's error result, which the model reads and can act on; only a malformed message, an
 * unknown method or an unknown tool is a JSON-RPC error.
 */
@Service
public class McpServerService implements McpServerUseCase {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    static final String LATEST_PROTOCOL = "2025-06-18";
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of(LATEST_PROTOCOL, "2025-03-26", "2024-11-05");

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;

    private static final String INSTRUCTIONS = """
            Voice AI agent platform. Typical flow: create_agent (or import_agent_pack), create_test_case, \
            run_evaluation, then poll get_evaluation_run until status is not RUNNING and read each failed \
            attempt's problems and transcript; fix with update_agent_draft and evaluate again. Publishing \
            to live calls is done by a person in the panel. list_calls and get_call read real calls.""";

    private final McpServerToolCatalog toolCatalog;
    private final ObjectMapper json = McpServerToolCatalog.newMapper();

    public McpServerService(McpServerToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    @Override
    public Optional<String> handleMessage(long companyId, String body) {
        JsonNode message;
        try {
            message = json.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            return Optional.of(error(NullNode.getInstance(), PARSE_ERROR, "Parse error"));
        }
        if (message == null || !message.isObject()) {
            // A JSON array was a batch, which the protocol dropped in 2025-06-18.
            return Optional.of(error(NullNode.getInstance(), INVALID_REQUEST, "Invalid Request"));
        }
        JsonNode id = message.get("id");
        if (!message.path("method").isTextual()) {
            // A client's answer to a request of ours — this server sends none — needs no reply.
            return message.has("result") || message.has("error") ? Optional.empty()
                    : Optional.of(error(id != null ? id : NullNode.getInstance(), INVALID_REQUEST, "Invalid Request"));
        }
        if (id == null) {
            return Optional.empty();
        }
        String method = message.get("method").asText();
        JsonNode params = message.path("params");
        return Optional.of(switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, json.createObjectNode());
            case "tools/list" -> result(id, json.createObjectNode().set("tools", json.valueToTree(toolCatalog.findVisibleTools())));
            case "tools/call" -> callTool(companyId, id, params);
            default -> error(id, METHOD_NOT_FOUND, "Method not found: " + method);
        });
    }

    @Override
    public List<McpTool> findServerTools() {
        return toolCatalog.findVisibleTools().stream()
                .map(tool -> new McpTool(tool.path("name").asText(), tool.path("description").asText(),
                        tool.path("inputSchema").toString(), false, false))
                .toList();
    }

    @Override
    public String callServerToolAsDraftOnly(long companyId, String name, String argumentsJson) {
        JsonNode arguments;
        try {
            arguments = json.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (JsonProcessingException e) {
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(e.getOriginalMessage()), name);
        }
        ObjectNode argumentObject = arguments instanceof ObjectNode object ? object : json.createObjectNode();
        try {
            return json.writeValueAsString(toolCatalog.callTool(companyId, name, argumentObject, true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MCP tool " + name + " returned a value that cannot be written as JSON", e);
        }
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        ObjectNode answer = json.createObjectNode();
        answer.put("protocolVersion", SUPPORTED_PROTOCOLS.contains(requested) ? requested : LATEST_PROTOCOL);
        answer.putObject("capabilities").putObject("tools").put("listChanged", false);
        answer.putObject("serverInfo").put("name", "robot-call").put("version", "1.0");
        answer.put("instructions", INSTRUCTIONS);
        return answer;
    }

    private String callTool(long companyId, JsonNode id, JsonNode params) {
        String name = params.path("name").asText("");
        if (!toolCatalog.existsTool(name)) {
            return error(id, INVALID_PARAMS, "Unknown tool: " + name);
        }
        JsonNode arguments = params.get("arguments");
        ObjectNode argumentObject = arguments instanceof ObjectNode object ? object : json.createObjectNode();
        try {
            Object value = toolCatalog.callTool(companyId, name, argumentObject, false);
            return result(id, toolResult(json.writeValueAsString(value), false));
        } catch (AppException e) {
            log.info("MCP tool {} refused for company {}: {}", name, companyId, e.code());
            return result(id, toolResult(describe(e), true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MCP tool " + name + " returned a value that cannot be written as JSON", e);
        }
    }

    /** The code first: the model can branch on it, and a person reading the transcript can look it up. */
    private static String describe(AppException e) {
        StringBuilder text = new StringBuilder(e.code().name()).append(": ").append(e.getMessage());
        if (e.errors() != null) {
            e.errors().forEach(problem -> text.append("\n- ").append(problem));
        }
        return text.toString();
    }

    private ObjectNode toolResult(String text, boolean isError) {
        ObjectNode result = json.createObjectNode();
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", isError);
        return result;
    }

    private String result(JsonNode id, JsonNode result) {
        ObjectNode envelope = envelope(id);
        envelope.set("result", result);
        return envelope.toString();
    }

    private String error(JsonNode id, int code, String message) {
        ObjectNode envelope = envelope(id);
        envelope.putObject("error").put("code", code).put("message", message);
        return envelope.toString();
    }

    private ObjectNode envelope(JsonNode id) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.set("id", id);
        return envelope;
    }
}
