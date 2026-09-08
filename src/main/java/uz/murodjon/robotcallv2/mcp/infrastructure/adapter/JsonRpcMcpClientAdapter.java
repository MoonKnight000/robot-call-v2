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
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * <p>Every request re-screens the address with {@link PublicUrlGuard} and refuses
 * redirects, for the same reason every other outbound call in this application does: the
 * URL was typed by a customer.
 */
@Component
public class JsonRpcMcpClientAdapter implements McpClientPort {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcMcpClientAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    // Redirects are never followed: the guard screens the address that was configured,
    // and a 302 is the far end picking a second one after that check has passed.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public List<McpTool> listTools(McpConnection connection, String bearerToken) {
        JsonNode result = rpc(connection, bearerToken, "tools/list", MAPPER.createObjectNode(), LIST_TIMEOUT);
        JsonNode tools = result.path("tools");
        if (!tools.isArray()) {
            throw new ExternalServiceException(ErrorCode.MCP_RESPONSE_INVALID, SERVICE,
                    connection.name(), "tools/list did not answer with a tools array");
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
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", MAPPER.valueToTree(arguments == null ? Map.of() : arguments));

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

        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", requestIds.incrementAndGet());
        envelope.put("method", method);
        envelope.set("params", params);

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    // Both, because a streamable-HTTP server may answer either way; the
                    // two calls made here have a plain JSON answer in practice.
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(envelope.toString(), StandardCharsets.UTF_8));
            if (bearerToken != null && !bearerToken.isBlank()) {
                request.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<InputStream> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            String body = readCapped(response.body());

            if (status == 401 || status == 403) {
                throw new ExternalServiceException(ErrorCode.MCP_AUTH_REQUIRED, SERVICE, connection.name());
            }
            if (status / 100 != 2) {
                throw new ExternalServiceException(ErrorCode.MCP_REQUEST_FAILED, SERVICE,
                        connection.name(), status);
            }

            JsonNode envelopeNode = MAPPER.readTree(stripEventStream(body));
            if (envelopeNode.has("error")) {
                throw new ExternalServiceException(ErrorCode.MCP_REQUEST_FAILED, SERVICE,
                        connection.name(), envelopeNode.path("error").path("message").asText("error"));
            }
            return envelopeNode.path("result");
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.MCP_REQUEST_FAILED, SERVICE, e,
                    connection.name(), "interrupted");
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

    private static String readCapped(InputStream body) throws IOException {
        try (InputStream stream = body) {
            return new String(stream.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
        }
    }
}
