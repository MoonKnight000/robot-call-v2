package uz.murodjon.robotcallv2.mcp.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.mcp.application.port.output.McpClientPort;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Speaks MCP over its streamable-HTTP transport: JSON-RPC posted to one URL.
 *
 * <p>Written against the protocol rather than pulled in as an SDK. The connections here
 * are per company and created at runtime from a database row, so the client has to be
 * built per request anyway — and the two calls that matter, {@code tools/list} and
 * {@code tools/call}, are a POST each. What an SDK would have brought instead is a second
 * HTTP stack whose timeouts and address screening are not ours to set, on a path that runs
 * while somebody is waiting on the phone.
 *
 * <p>Every request re-screens the address with {@link PublicUrlGuard}, goes through the
 * shared {@link PublicHttpClient} (DNS pinned to public addresses) and refuses redirects,
 * for the same reason every other outbound call in this application does: the URL was
 * typed by a customer.
 */
@Component
public class JsonRpcMcpClientAdapter implements McpClientPort {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcMcpClientAdapter.class);
    private final ObjectMapper objectMapper;

    private static final String SERVICE = "mcp";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    /**
     * Deliberately short. This runs between the caller finishing a sentence and the agent
     * answering; past a couple of seconds the tool has already cost more than it is worth.
     */
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(6);

    /** Read at all. Anything past this is not a tool result. */
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    /** Handed to the model. Everything beyond is prompt it pays for and cannot use. */
    private static final int MAX_RESULT_CHARS = 4000;

    private final AtomicLong requestIds = new AtomicLong();

    // The shared client: DNS pinned to public addresses, and redirects never followed —
    // the guard screens the address that was configured, and a 302 is the far end picking
    // a second one after that check has passed.
    private final PublicHttpClient httpClient = PublicHttpClient.create(Duration.ofSeconds(5), LIST_TIMEOUT);

    public JsonRpcMcpClientAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<McpTool> listTools(McpConnection connection, String bearerToken) {
        JsonNode result = rpc(connection, bearerToken, "tools/list", objectMapper.createObjectNode(), LIST_TIMEOUT);
        JsonNode tools = result.path("tools");
        if (!tools.isArray()) {
            throw new ExternalServiceException(ErrorCode.MCP_RESPONSE_INVALID, SERVICE, connection.name());
        }
        List<McpTool> discovered = new ArrayList<>();
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            JsonNode annotations = tool.path("annotations");
            discovered.add(new McpTool(
                    name,
                    tool.path("description").asText(""),
                    tool.has("inputSchema") ? tool.get("inputSchema").toString() : null,
                    annotations.path("readOnlyHint").asBoolean(false),
                    annotations.path("destructiveHint").asBoolean(false)));
        }
        return discovered;
    }

    @Override
    public String callTool(McpConnection connection, String bearerToken, String toolName,
                           Map<String, Object> arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments == null ? Map.of() : arguments));

        JsonNode result = rpc(connection, bearerToken, "tools/call", params, CALL_TIMEOUT);
        String text = readContent(result);
        return text.length() <= MAX_RESULT_CHARS ? text : text.substring(0, MAX_RESULT_CHARS);
    }

    /**
     * An MCP result is a list of content parts. Only the text ones are of any use to a
     * model that is about to say the answer out loud, so images and blobs are skipped
     * rather than described.
     */
    private static String readContent(JsonNode result) {
        JsonNode content = result.path("content");
        if (!content.isArray()) {
            return result.toString();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText())) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.path("text").asText(""));
            }
        }
        return text.isEmpty() ? result.toString() : text.toString();
    }

    private JsonNode rpc(McpConnection connection, String bearerToken, String method, JsonNode params,
                         Duration timeout) {
        URI uri = PublicUrlGuard.parsePublic(connection.url());
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ExternalServiceException(ErrorCode.MCP_URL_INVALID, SERVICE, connection.name());
        }

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", requestIds.incrementAndGet());
        envelope.put("method", method);
        envelope.set("params", params);

        try {
            PublicHttpRequest request = PublicHttpRequest.create("POST", uri)
                    .setTimeout(timeout)
                    // Read at all; anything past this is not a tool result.
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES)
                    .addHeader("Content-Type", "application/json")
                    // Both, because a streamable-HTTP server may answer either way; the
                    // two calls made here have a plain JSON answer in practice.
                    .addHeader("Accept", "application/json, text/event-stream")
                    .addHeader("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .setBody(envelope.toString());
            if (bearerToken != null && !bearerToken.isBlank()) {
                request.addHeader("Authorization", "Bearer " + bearerToken);
            }

            PublicHttpResponse response = httpClient.send(request);
            int status = response.statusCode();
            String body = response.readBodyText();

            if (status == 401 || status == 403) {
                throw new ExternalServiceException(ErrorCode.MCP_AUTH_REQUIRED, SERVICE, connection.name());
            }
            if (status / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.MCP_REQUEST_FAILED, SERVICE,
                        connection.name(), status);
            }

            JsonNode envelopeNode = objectMapper.readTree(stripEventStream(body));
            if (envelopeNode.has("error")) {
                JsonNode error = envelopeNode.path("error");
                // The server's own wording is vendor text: it goes to the log, the message
                // carries only the JSON-RPC error code.
                log.warn("MCP {} on '{}' answered error {}: {}", method, connection.name(),
                        error.path("code").asInt(), error.path("message").asText(""));
                throw new ExternalServiceException(ErrorCode.MCP_SERVER_ERROR, SERVICE,
                        connection.name(), error.path("code").asInt());
            }
            return envelopeNode.path("result");
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.MCP_REQUEST_INTERRUPTED, SERVICE, e, connection.name());
        } catch (Exception e) {
            log.debug("MCP {} failed for {}: {}", method, connection.name(), e.getMessage());
            throw new ExternalServiceException(ErrorCode.MCP_REQUEST_FAILED, SERVICE, e,
                    connection.name(), String.valueOf(e.getMessage()));
        }
    }

    /**
     * A server that answered as an event stream sends {@code data: {...}} lines. Taking
     * the first of them is enough here: both calls made by this adapter are a single
     * request/response, not a subscription.
     */
    private static String stripEventStream(String body) {
        String trimmed = body.trim();
        if (!trimmed.startsWith("data:")) {
            return trimmed;
        }
        return trimmed.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(trimmed);
    }
}
