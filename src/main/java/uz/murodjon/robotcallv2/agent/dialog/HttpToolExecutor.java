package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calls the REST endpoints a company configured as tools, on behalf of the model.
 *
 * <p>The company's secrets are read once per call and substituted into the URL and the
 * headers. Two rules follow from that and are the reason this class does its own
 * placeholder handling instead of asking the service per string: secrets are resolved
 * <em>before</em> anything the caller or the model supplied, so a value carrying
 * {@code {{secrets.X}}} cannot make the request fetch a credential; and every string that
 * leaves here for a log or for the model goes through
 * {@link SecretPlaceholders#redact(String, java.util.Collection)} first, because a
 * resolved URL is the credential.
 *
 * <p>What comes back is treated as text this system did not write (NEXT-PLAN A.4): only
 * the fields a tool's {@code responseMapping} names reach the model when it has one, and
 * every text value goes through {@link PromptSafeText} on the way.
 */
@Component
public class HttpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpToolExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * The body parameter whose value is the whole request body rather than one field of
     * it. See {@link #rawBodyTemplate}.
     */
    private static final String RAW_BODY_PARAM = "__raw__";

    /**
     * How much of the answer is read at all. An endpoint that streams a gigabyte would
     * otherwise be read into memory on a call thread, and none of it is a tool result.
     */
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    /**
     * How much of it reaches the model. Everything past this is prompt the model pays for
     * and cannot use; a tool that needs more than this is answering the wrong question.
     */
    private static final int MAX_RESPONSE_CHARS_FOR_MODEL = 4000;

    /** Pause before the first retry; the second waits twice as long. */
    private static final long RETRY_BACKOFF_MS = 300;

    /** Stands in for a conversation when a tool is run from the panel's "Test" button. */
    private static final DialogOutcomeSink DRY_RUN = new DryRunSink();

    /**
     * Opens the answer to a write tool's first call: nothing was sent, the caller is to
     * confirm first. {@code TurnRunner} carries a tool note holding it into the next turn.
     */
    static final String CONFIRMATION_REQUIRED = "TASDIQ KERAK";

    /** Added to the description of a tool whose calls are held for the caller's yes. */
    private static final String CONFIRMATION_HINT = " Ma'lumot to'liq bo'lishi bilan shu tool'ni chaqiring: "
            + "birinchi chaqiruv so'rovni yubormaydi, mijozga o'qib berib tasdiqlatadi. O'zingiz alohida tasdiq so'ramang.";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** The JSON Schema keywords a function declaration takes; anything else in a bodySchema stays out of it. */
    private static final Set<String> MODEL_SCHEMA_KEYWORDS = Set.of("type", "description", "properties",
            "required", "items", "enum", "format", "minimum", "maximum", "minItems", "maxItems", "nullable");

    /** Arguments written with every object's keys in order, so the same call is the same string. */
    private static final ObjectWriter CANONICAL = MAPPER.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final PublicHttpClient httpClient;
    private final SecretUseCase secretUseCase;
    private final ToolExecutionUseCase toolExecutionUseCase;
    private final ToolOAuthTokens oauthTokens;

    public HttpToolExecutor(SecretUseCase secretUseCase, ToolExecutionUseCase toolExecutionUseCase,
                            ToolOAuthTokens oauthTokens) {
        this.secretUseCase = secretUseCase;
        this.toolExecutionUseCase = toolExecutionUseCase;
        this.oauthTokens = oauthTokens;
        // The shared client: the host is resolved only to public addresses at connect time,
        // and redirects are never followed — PublicUrlGuard screens the address that was
        // configured, and a 302 is the endpoint picking a second one after that check has
        // passed, which is exactly how such a filter is walked around.
        this.httpClient = PublicHttpClient.create(Duration.ofSeconds(10), Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    public ToolCallback buildCallback(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables) {
        String toolName = sanitizeToolName(tool.name());
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolName, args -> execute(tool, session, callVariables, args))
                .description(tool.requiresConfirmation() ? tool.description() + CONFIRMATION_HINT : tool.description())
                .inputSchema(generateSchema(tool))
                .inputType(Map.class)
                .build();
    }

    /**
     * One run of {@code tool} outside any call — the panel's "Test" button. Same request,
     * same address guard, same answer the model would get; there is simply no conversation
     * for the tool to act on, and no per-call limit to count against.
     */
    public String executeForTest(Tool tool, Map<String, Object> callVariables, Map<String, Object> args) {
        return execute(tool, DRY_RUN, callVariables != null ? callVariables : Map.of(), args != null ? args : Map.of());
    }

    public String execute(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables, Map<String, Object> args) {
        String canonicalArgs = canonical(args);
        // A write the caller has not said yes to is not sent (UNIVERSAL-OPERATOR-PLAN B.3).
        // Held here rather than asked of the prompt: a stranger's voice writing into the
        // company's system is exactly what a prompt cannot be trusted to hold. The panel's
        // test run has no caller to ask.
        if (tool.requiresConfirmation() && session != DRY_RUN) {
            if (!isConfirmed(tool, session, canonicalArgs)) {
                return holdForConfirmation(tool, session, args, canonicalArgs);
            }
            session.setPendingConfirmation(tool.name(), null);
        }
        // A model stuck in a loop calls the same lookup over and over, and each one is a
        // request to the company's API and a pause the caller sits through.
        int callNumber = session.countToolCall(tool.name());
        if (tool.maxCallsPerCall() > 0 && callNumber > tool.maxCallsPerCall()) {
            log.warn("[{}] Tool '{}' refused: already called {} times on this call",
                    session.channelId(), tool.name(), tool.maxCallsPerCall());
            return limitReached(tool.maxCallsPerCall());
        }
        if (tool.forcePreToolSpeech() && tool.preToolSpeech() != null && !tool.preToolSpeech().isBlank()) {
            session.addToolReply(tool.preToolSpeech().trim());
        }

        Map<String, String> secrets = tool.companyId() != null
                ? secretUseCase.findDecryptedSecrets(tool.companyId())
                : Map.of();
        Long callAttemptId = session == DRY_RUN ? null : callAttemptId(callVariables);
        String argsHash = ToolExecution.hashArguments(canonicalArgs);
        long started = System.nanoTime();

        try {
            int timeoutSeconds = (tool.responseTimeoutSecs() != null && tool.responseTimeoutSecs() > 0)
                    ? tool.responseTimeoutSecs()
                    : DEFAULT_TIMEOUT_SECONDS;

            String resolvedUrl = resolveUrl(tool.apiUrl(), tool.apiPathParams(), tool.apiQueryParams(),
                    callVariables, args, secrets);
            // Screened again here, not only when the tool was saved: a path or query value
            // the model supplied has been substituted in by now, and it can point the
            // request somewhere the stored address did not.
            URI uri = PublicUrlGuard.parsePublic(resolvedUrl);
            if (uri == null) {
                log.warn("[{}] Tool '{}' was not called: its address is not publicly callable",
                        session.channelId(), tool.name());
                return blocked();
            }
            String method = (tool.apiMethod() != null ? tool.apiMethod().toUpperCase() : "POST");

            PublicHttpRequest request = PublicHttpRequest.create(method, uri)
                    .setTimeout(Duration.ofSeconds(timeoutSeconds))
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES);

            // Apply Headers
            boolean hasContentType = false;
            if (tool.apiHeaders() != null) {
                for (ToolKeyValuePair header : tool.apiHeaders()) {
                    // A tool with its own OAuth client authenticates with the token below.
                    if (header.key() != null && !header.key().isBlank()
                            && !(tool.auth() != null && AUTHORIZATION_HEADER.equalsIgnoreCase(header.key().trim()))) {
                        String headerValue = resolveTemplate(header.value(), callVariables, args, secrets);
                        request.addHeader(header.key().trim(), headerValue);
                        if ("content-type".equalsIgnoreCase(header.key().trim())) {
                            hasContentType = true;
                        }
                    }
                }
            }

            // Fresh or cached (UNIVERSAL-OPERATOR-PLAN B.5). A token that cannot be had fails the
            // call below like any other request that did not go out.
            if (tool.auth() != null && tool.companyId() != null) {
                request.addHeader(AUTHORIZATION_HEADER,
                        "Bearer " + oauthTokens.findAccessToken(tool.companyId(), tool.auth(), secrets));
            }

            // A retried POST must not become a second order: the key is the same on every
            // retry of one call, so an endpoint that honours it answers the repeat with the first.
            if (tool.effect() == ToolEffect.WRITE && !hasHeader(tool, IDEMPOTENCY_KEY_HEADER)) {
                request.addHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey(tool, callAttemptId, callNumber));
            }

            // Build Body
            if (!("GET".equals(method) || "DELETE".equals(method))) {
                if (!hasContentType) {
                    request.addHeader("Content-Type", "application/json");
                }
                request.setBody(tool.bodySchema().isEmpty()
                        ? buildBody(tool.apiBody(), callVariables, args, secrets)
                        : bodyFromSchema(tool.bodySchema(), callVariables, args, secrets));
            }

            log.info("[{}] Executing HTTP tool '{}' {} {}", session.channelId(), tool.name(), method,
                    SecretPlaceholders.redact(resolvedUrl, secrets.values()));
            PublicHttpResponse response = sendWithRetry(tool, session, request);

            log.info("[{}] Tool '{}' responded with status {}", session.channelId(), tool.name(), response.statusCode());
            if (response.statusCode() == 401 && tool.auth() != null && tool.companyId() != null) {
                // Refused before it ran out — revoked or rotated. The next call asks for a new one.
                oauthTokens.evictToken(tool.companyId(), tool.auth(), secrets);
            }

            // Already capped at MAX_RESPONSE_BYTES; the rest of the stream was never read.
            String responseBody = response.readBodyText();
            if (tool.companyId() != null) {
                toolExecutionUseCase.recordExecution(ToolExecution.answered(tool.companyId(), tool.id(), callAttemptId,
                        tool.name(), response.statusCode(), elapsedMs(started), argsHash,
                        SecretPlaceholders.redact(responseBody, secrets.values())));
            }
            session.recordOutcome("tool_" + tool.name(), Map.of(
                    "status", response.statusCode(),
                    "response", responseBody
            ));
            return forModel(tool, session, response.statusCode(), responseBody);

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // The realtime engine's tool deadline cancels by interrupting; keep it visible.
                Thread.currentThread().interrupt();
            }
            // The detail stays on the server. A connection or URI failure names the address
            // it failed on, and that address has the credential in it by this point — the
            // model has no use for it and repeats what it is told out loud.
            String reason = SecretPlaceholders.redact(String.valueOf(e.getMessage()), secrets.values());
            log.error("[{}] Tool '{}' execution failed: {}", session.channelId(), tool.name(), reason);
            if (tool.companyId() != null) {
                toolExecutionUseCase.recordExecution(ToolExecution.failed(tool.companyId(), tool.id(), callAttemptId,
                        tool.name(), elapsedMs(started), argsHash, reason));
            }
            if (session == DRY_RUN) {
                // A test run from the panel: the one reading the result owns the tool, and a
                // failure whose reason they cannot see is no test at all.
                throw new ExternalServiceException(ErrorCode.TOOL_EXECUTION_FAILED, "tool", e, tool.name(), reason);
            }
            ObjectNode errorNode = MAPPER.createObjectNode();
            errorNode.put("status", 500);
            errorNode.put("error", "Tool execution failed");
            return errorNode.toString();
        }
    }

    /**
     * Sends the request, and again after a 5xx or a network failure up to the tool's
     * {@code retryAttempts}. A 4xx is never retried: the same request gets the same answer.
     */
    private PublicHttpResponse sendWithRetry(Tool tool, DialogOutcomeSink session, PublicHttpRequest request)
            throws IOException, InterruptedException {
        int retries = tool.retryAttempts();
        for (int attempt = 0; ; attempt++) {
            try {
                PublicHttpResponse response = httpClient.send(request);
                if (response.statusCode() < 500 || attempt >= retries) {
                    return response;
                }
                log.warn("[{}] Tool '{}' answered {}, retrying ({}/{})", session.channelId(), tool.name(),
                        response.statusCode(), attempt + 1, retries);
            } catch (IOException e) {
                if (attempt >= retries) {
                    throw e;
                }
                log.warn("[{}] Tool '{}' failed ({}), retrying ({}/{})", session.channelId(), tool.name(),
                        e.getClass().getSimpleName(), attempt + 1, retries);
            }
            Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1));
        }
    }

    /**
     * What the model is handed: the status and the answer — only the mapped fields when the
     * tool has a {@code responseMapping} — with every text value made prompt-safe, then cut
     * to {@link #MAX_RESPONSE_CHARS_FOR_MODEL}.
     */
    private String forModel(Tool tool, DialogOutcomeSink session, int status, String responseBody) throws IOException {
        JsonNode data;
        try {
            data = MAPPER.readTree(responseBody);
        } catch (Exception notJson) {
            data = null;
        }
        if (data == null || data.isMissingNode()) {
            data = TextNode.valueOf(responseBody);
        } else if (!tool.responseMapping().isEmpty()) {
            data = applyMapping(data, tool.responseMapping());
        }
        data = promptSafe(data);
        if (status >= 200 && status < 300) {
            recordFindings(tool, session, data);
        }

        ObjectNode resultNode = MAPPER.createObjectNode();
        resultNode.put("status", status);
        resultNode.set("data", data);
        String result = MAPPER.writeValueAsString(resultNode);
        if (result.length() <= MAX_RESPONSE_CHARS_FOR_MODEL) {
            return result;
        }
        log.warn("[{}] Tool '{}' answered with {} chars — truncated for the model",
                session.channelId(), tool.name(), result.length());
        String dataText = data.isTextual() ? data.asText() : MAPPER.writeValueAsString(data);
        ObjectNode truncatedNode = MAPPER.createObjectNode();
        truncatedNode.put("status", status);
        truncatedNode.put("data", dataText.substring(0, Math.min(dataText.length(), MAX_RESPONSE_CHARS_FOR_MODEL)));
        truncatedNode.put("truncated", true);
        return MAPPER.writeValueAsString(truncatedNode);
    }

    /**
     * What a successful answer leaves on the call. The mapped fields become the call's
     * outcome under their mapping names — how a company tool reaches a scenario's goal
     * ({@code orderId} from {@code create_order}, UNIVERSAL-OPERATOR-PLAN A.1) — and every
     * figure the model was shown becomes one it may say out loud ({@link FactGuard}): a price
     * the catalog answered with is given, not invented. Only a 2xx answer counts, because an
     * error body can echo back the very figure the model sent.
     */
    private static void recordFindings(Tool tool, DialogOutcomeSink session, JsonNode data) {
        if (!tool.responseMapping().isEmpty() && data.isObject()) {
            data.fields().forEachRemaining(field ->
                    session.recordOutcome(field.getKey(), MAPPER.convertValue(field.getValue(), Object.class)));
        }
        List<BigDecimal> figures = new ArrayList<>();
        collectFigures(data, figures);
        session.recordToolFigures(figures);
    }

    private static void collectFigures(JsonNode node, List<BigDecimal> figures) {
        if (node.isNumber()) {
            figures.add(node.decimalValue());
        } else if (node.isTextual()) {
            figures.addAll(FactGuard.figuresIn(node.asText()));
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectFigures(child, figures));
        }
    }

    /**
     * The fields a tool's {@code responseMapping} picks out of the answer, each under its
     * own name. A path that is not in this answer is left out rather than sent as null.
     */
    static JsonNode applyMapping(JsonNode root, Map<String, String> mapping) {
        ObjectNode mapped = MAPPER.createObjectNode();
        mapping.forEach((name, path) -> {
            JsonNode value = root.at(toPointer(path));
            if (!value.isMissingNode()) {
                mapped.set(name, value);
            }
        });
        return mapped;
    }

    /**
     * A dotted JSONPath ({@code $.data.items[0].status}) as the JSON Pointer Jackson reads
     * ({@code /data/items/0/status}). {@code $} alone is the whole answer. Bracketed names
     * and wildcards are not supported — a mapping names one value.
     */
    static String toPointer(String path) {
        String trimmed = path.trim();
        StringBuilder pointer = new StringBuilder();
        StringBuilder segment = new StringBuilder();
        for (int i = trimmed.startsWith("$") ? 1 : 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.' || c == '[' || c == ']') {
                appendSegment(pointer, segment);
            } else {
                segment.append(c);
            }
        }
        appendSegment(pointer, segment);
        return pointer.toString();
    }

    private static void appendSegment(StringBuilder pointer, StringBuilder segment) {
        if (segment.isEmpty()) {
            return;
        }
        pointer.append('/').append(segment.toString().replace("~", "~0").replace("/", "~1"));
        segment.setLength(0);
    }

    /** The same tree with every text value passed through {@link PromptSafeText}. */
    private static JsonNode promptSafe(JsonNode node) {
        if (node.isTextual()) {
            String safe = PromptSafeText.sanitize(node.asText(), MAX_RESPONSE_CHARS_FOR_MODEL);
            return safe == null ? NullNode.getInstance() : TextNode.valueOf(safe);
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            node.fields().forEachRemaining(field -> copy.set(field.getKey(), promptSafe(field.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(element -> copy.add(promptSafe(element)));
            return copy;
        }
        return node;
    }

    /** What the model is told once a tool has been called as often as one call allows. */
    private static String limitReached(int limit) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", 429);
        node.put("error", "Bu amal shu qo'ng'iroqda " + limit + " marta bajarildi. Qayta chaqirmang, "
                + "olingan natija bilan suhbatni davom ettiring.");
        return node.toString();
    }

    /**
     * What the model is told when the address was refused. Deliberately the same shape as
     * a failure: the model's job is to carry on the conversation without the tool, not to
     * learn which addresses this server declines to call.
     */
    private static String blocked() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", 400);
        node.put("error", "Tool execution failed");
        return node.toString();
    }

    private String resolveUrl(String rawUrl, List<ToolParamConfig> pathParams, List<ToolParamConfig> queryParams,
                              Map<String, Object> callVariables, Map<String, Object> args,
                              Map<String, String> secrets) {
        // Secrets first: after this line the string holds credentials, and a path or query
        // value that happens to contain a placeholder is just text.
        String url = SecretPlaceholders.resolve(rawUrl, secrets);
        if (pathParams != null) {
            for (ToolParamConfig param : pathParams) {
                Object value = resolveParamValue(param, callVariables, args);
                String pathValue = value != null ? value.toString() : "";
                url = url.replace("{" + param.name() + "}", URLEncoder.encode(pathValue, StandardCharsets.UTF_8));
            }
        }

        StringBuilder queryString = new StringBuilder();
        if (queryParams != null && !queryParams.isEmpty()) {
            for (ToolParamConfig param : queryParams) {
                Object value = resolveParamValue(param, callVariables, args);
                if (value != null) {
                    if (!queryString.isEmpty()) {
                        queryString.append("&");
                    }
                    queryString.append(URLEncoder.encode(param.name(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
                }
            }
        }

        if (!queryString.isEmpty()) {
            url += (url.contains("?") ? "&" : "?") + queryString;
        }
        return url;
    }

    /**
     * The request body: one field per configured parameter, unless the tool supplied a
     * whole body itself through {@link #RAW_BODY_PARAM}.
     */
    private String buildBody(List<ToolParamConfig> bodyParams, Map<String, Object> callVariables,
                             Map<String, Object> args, Map<String, String> secrets) {
        String template = rawBodyTemplate(bodyParams);
        if (template != null) {
            return renderRawBody(template, callVariables, args, secrets);
        }
        ObjectNode node = MAPPER.createObjectNode();
        if (bodyParams != null) {
            for (ToolParamConfig param : bodyParams) {
                Object value = resolveParamValue(param, callVariables, args);
                if (value != null) {
                    if (value instanceof Number number) {
                        node.put(param.name(), number.doubleValue());
                    } else if (value instanceof Boolean flag) {
                        node.put(param.name(), flag);
                    } else {
                        node.put(param.name(), value.toString());
                    }
                }
            }
        }
        return node.toString();
    }

    /**
     * The body template a tool configured, or {@code null} when it configured fields.
     *
     * <p>A field list can only describe a flat JSON object of scalars, which is most
     * endpoints and not all of them: Uysot's {@code POST /v1/open-api/lead-note/{id}/list}
     * takes a JSON <em>array</em>, and its filters take nested objects and arrays. Rather
     * than grow a schema language in the tool table, a tool may name one static body
     * parameter {@code __raw__} whose value is the body itself, with {@code {{name}}}
     * wherever a call variable, a model argument or a secret belongs.
     */
    private static String rawBodyTemplate(List<ToolParamConfig> bodyParams) {
        if (bodyParams == null) {
            return null;
        }
        for (ToolParamConfig param : bodyParams) {
            if (RAW_BODY_PARAM.equals(param.name()) && param.value() != null) {
                return param.value().toString();
            }
        }
        return null;
    }

    /**
     * Substitutes into a body template. Unlike {@link #resolveTemplate}, every substituted
     * value is JSON-escaped: these land inside a document that has to stay parseable, and
     * a client name with a quote in it would otherwise end the request early. Numbers and
     * booleans go in bare, so a template can write {@code "leadId": {{lead_id}}}.
     */
    private String renderRawBody(String template, Map<String, Object> callVariables,
                                 Map<String, Object> args, Map<String, String> secrets) {
        // Secrets first, for the same reason as everywhere else here: after this line a
        // call variable reading "{{secrets.CRM_TOKEN}}" is text, not the token.
        String body = SecretPlaceholders.resolve(template, secrets);
        if (callVariables != null) {
            for (Map.Entry<String, Object> variable : callVariables.entrySet()) {
                body = body.replace("{{" + variable.getKey() + "}}", jsonValue(variable.getValue()));
            }
        }
        if (args != null) {
            for (Map.Entry<String, Object> argument : args.entrySet()) {
                body = body.replace("{{" + argument.getKey() + "}}", jsonValue(argument.getValue()));
            }
        }
        return body;
    }

    /** A value as it may appear inside a JSON document, without its surrounding quotes. */
    private static String jsonValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        try {
            String quoted = MAPPER.writeValueAsString(value.toString());
            return quoted.substring(1, quoted.length() - 1);
        } catch (Exception e) {
            return "";
        }
    }

    private Object resolveParamValue(ToolParamConfig config, Map<String, Object> callVariables, Map<String, Object> args) {
        return switch (config.valueType()) {
            case STATIC_VALUE -> config.value();
            case DYNAMIC_VARIABLE -> {
                String variableName = config.value() != null ? config.value().toString() : config.name();
                yield callVariables != null ? callVariables.get(variableName) : null;
            }
            case LLM_PROMPT -> args != null ? args.get(config.name()) : null;
        };
    }

    private String resolveTemplate(String template, Map<String, Object> callVariables, Map<String, Object> args,
                                   Map<String, String> secrets) {
        if (template == null) {
            return "";
        }
        // Same order as resolveUrl, and for the same reason: a call variable or a model
        // argument reading "{{secrets.CRM_TOKEN}}" must stay that text, not become the token.
        String resolved = SecretPlaceholders.resolve(template, secrets);
        if (callVariables != null) {
            for (Map.Entry<String, Object> variable : callVariables.entrySet()) {
                resolved = resolved.replace("{{" + variable.getKey() + "}}",
                        variable.getValue() != null ? variable.getValue().toString() : "");
            }
        }
        if (args != null) {
            for (Map.Entry<String, Object> argument : args.entrySet()) {
                resolved = resolved.replace("{{" + argument.getKey() + "}}",
                        argument.getValue() != null ? argument.getValue().toString() : "");
            }
        }
        return resolved;
    }

    private String generateSchema(Tool tool) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        collectLlmParams(tool.apiPathParams(), properties, required);
        collectLlmParams(tool.apiQueryParams(), properties, required);
        collectLlmParams(tool.apiBody(), properties, required);
        // A nested body: its fields are the model's top-level arguments, less the ones the
        // configuration fills (UNIVERSAL-OPERATOR-PLAN B.1).
        if (!tool.bodySchema().isEmpty()) {
            JsonNode body = schemaForModel(MAPPER.valueToTree(tool.bodySchema()));
            body.path("properties").fields().forEachRemaining(field -> properties.set(field.getKey(), field.getValue()));
            body.path("required").forEach(required::add);
        }

        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    private void collectLlmParams(List<ToolParamConfig> configs, ObjectNode properties, ArrayNode required) {
        if (configs == null) {
            return;
        }
        for (ToolParamConfig config : configs) {
            if (config.valueType() != ToolParamValueType.LLM_PROMPT) {
                continue;
            }
            ObjectNode property = properties.putObject(config.name());
            String type = config.type() != null ? config.type().toLowerCase() : "string";
            property.put("type", switch (type) {
                case "number", "integer" -> "number";
                case "boolean" -> "boolean";
                default -> "string";
            });
            if (config.description() != null && !config.description().isBlank()) {
                property.put("description", config.description());
            }
            if (config.allowedValues() != null && !config.allowedValues().isEmpty()) {
                ArrayNode enumArray = property.putArray("enum");
                config.allowedValues().forEach(enumArray::add);
            }
            if (config.required()) {
                required.add(config.name());
            }
        }
    }

    /**
     * Whether this call of a write tool is the caller's yes to the one read back to them:
     * the same arguments, read back before the caller last spoke, answered with a plain yes.
     * Anything else — changed arguments ("3 ta emas, 2 ta"), a second call in the same
     * breath, an answer that is not a yes — is read back again instead of sent.
     */
    private static boolean isConfirmed(Tool tool, DialogOutcomeSink session, String canonicalArgs) {
        PendingToolConfirmation pending = session.findPendingConfirmation(tool.name());
        return pending != null
                && pending.arguments().equals(canonicalArgs)
                && session.countCallerUtterances() > pending.callerUtterances()
                && Affirmations.saidByCaller(session.findLastCallerText());
    }

    /**
     * A write tool's call that is not sent yet. The arguments are held, the readback rides
     * as the line to speak when the turn has none of its own, and the model is told that
     * nothing went out and what the caller has to say.
     */
    private static String holdForConfirmation(Tool tool, DialogOutcomeSink session, Map<String, Object> args,
                                              String canonicalArgs) {
        session.setPendingConfirmation(tool.name(),
                new PendingToolConfirmation(canonicalArgs, session.countCallerUtterances()));
        String readback = readback(tool, args);
        if (readback != null) {
            session.addToolReply(readback);
        }
        log.info("[{}] Tool '{}' held for the caller's confirmation", session.channelId(), tool.name());
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", "CONFIRMATION_REQUIRED");
        node.put("note", CONFIRMATION_REQUIRED + ": so'rov hali YUBORILMADI. Mijozga "
                + (readback != null ? "shuni o'qib bering: \"" + readback + "\"" : "yuboriladigan ma'lumotni qisqa o'qib bering")
                + " va tasdiqlashini so'rang. Mijoz aniq \"ha\" desa — shu tool'ni AYNAN shu argumentlar bilan "
                + "qayta chaqiring. Mijoz biror narsani o'zgartirsa — yangi argumentlar bilan chaqiring, "
                + "ular ham qayta tasdiqlanadi.");
        if (readback == null && args != null) {
            node.set("args", MAPPER.valueToTree(args));
        }
        return node.toString();
    }

    /** The tool's {@code readbackTemplate} filled with this call's arguments, or null when it has none. */
    private static String readback(Tool tool, Map<String, Object> args) {
        String template = tool.readbackTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        if (args != null) {
            args.forEach((name, value) -> values.put(name, spoken(value)));
        }
        String rendered = PromptTemplateEngine.render(template, values);
        return rendered == null || rendered.isBlank() ? null : rendered.trim();
    }

    /** An argument as it is read out: a list item by item, an object by its values — "A1 2, B7 1". */
    private static String spoken(Object value) {
        if (value instanceof Map<?, ?> fields) {
            return fields.values().stream().map(HttpToolExecutor::spoken).collect(Collectors.joining(" "));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(HttpToolExecutor::spoken).collect(Collectors.joining(", "));
        }
        return value == null ? "" : value.toString();
    }

    /** The arguments as canonical JSON: what a confirmation covers and what the log hashes. */
    private static String canonical(Map<String, Object> args) {
        try {
            return CANONICAL.writeValueAsString(args == null ? Map.of() : args);
        } catch (Exception e) {
            return String.valueOf(args);
        }
    }

    /** The call attempt a live call runs under ({@code TurnTools} puts it there); null when there is none. */
    private static Long callAttemptId(Map<String, Object> callVariables) {
        Object id = callVariables == null ? null : callVariables.get("call_attempt_id");
        return id instanceof Number number && number.longValue() > 0 ? number.longValue() : null;
    }

    private static boolean hasHeader(Tool tool, String name) {
        return tool.apiHeaders().stream()
                .anyMatch(header -> header.key() != null && header.key().trim().equalsIgnoreCase(name));
    }

    /**
     * The same on every retry of one call, different for the next call: the call attempt,
     * the tool, and how many times this call has used it. A test run from the panel has no
     * call attempt and gets a fresh key each time — a tester pressing the button twice means it.
     */
    private static String idempotencyKey(Tool tool, Long callAttemptId, int callNumber) {
        String call = callAttemptId != null ? callAttemptId.toString() : "test-" + UUID.randomUUID();
        return call + ":" + sanitizeToolName(tool.name()) + ":" + callNumber;
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    /**
     * The part of a {@code bodySchema} the model fills (UNIVERSAL-OPERATOR-PLAN B.1). A field
     * whose value comes from the configuration, the call or a secret ({@value Tool#BODY_SOURCE_KEY})
     * is left out, and so is every keyword a function declaration does not take — a model
     * provider refuses a declaration it cannot read.
     */
    static JsonNode schemaForModel(JsonNode schema) {
        ObjectNode copy = MAPPER.createObjectNode();
        schema.fields().forEachRemaining(keyword -> {
            if (MODEL_SCHEMA_KEYWORDS.contains(keyword.getKey())) {
                copy.set(keyword.getKey(), keyword.getValue());
            }
        });
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            ObjectNode modelProperties = copy.putObject("properties");
            Set<String> sourced = new HashSet<>();
            properties.fields().forEachRemaining(field -> {
                if (field.getValue().has(Tool.BODY_SOURCE_KEY)) {
                    sourced.add(field.getKey());
                } else {
                    modelProperties.set(field.getKey(), schemaForModel(field.getValue()));
                }
            });
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                ArrayNode stillRequired = copy.putArray("required");
                required.forEach(name -> {
                    if (!sourced.contains(name.asText())) {
                        stillRequired.add(name);
                    }
                });
            }
        }
        JsonNode items = schema.get("items");
        if (items != null && items.isObject()) {
            copy.set("items", schemaForModel(items));
        }
        return copy;
    }

    /**
     * The request body a {@code bodySchema} describes: nested objects and arrays exactly as
     * the model sent them where the schema leaves a field to it, and the
     * {@value Tool#BODY_SOURCE_KEY} fields filled from the configuration, the call or a secret.
     * Only the fields the schema names go out — an argument the model made up is not sent —
     * and a secret comes from the schema alone, never from an argument.
     */
    static String bodyFromSchema(Map<String, Object> schema, Map<String, Object> callVariables,
                                 Map<String, Object> args, Map<String, String> secrets) {
        JsonNode body = fill(MAPPER.valueToTree(schema), MAPPER.valueToTree(args == null ? Map.of() : args), null,
                callVariables, secrets);
        return body == null ? "{}" : body.toString();
    }

    private static JsonNode fill(JsonNode schema, JsonNode given, String name,
                                 Map<String, Object> callVariables, Map<String, String> secrets) {
        if (schema.has(Tool.BODY_SOURCE_KEY)) {
            return sourcedValue(schema, name, callVariables, secrets);
        }
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            return given; // an array, a scalar or a free-form object: as the model sent it
        }
        ObjectNode filled = MAPPER.createObjectNode();
        properties.fields().forEachRemaining(field -> {
            JsonNode child = given != null && given.isObject() ? given.get(field.getKey()) : null;
            JsonNode value = fill(field.getValue(), child, field.getKey(), callVariables, secrets);
            if (value != null && !value.isNull()) {
                filled.set(field.getKey(), value);
            }
        });
        return filled.isEmpty() && given == null ? null : filled;
    }

    private static JsonNode sourcedValue(JsonNode schema, String name, Map<String, Object> callVariables,
                                         Map<String, String> secrets) {
        JsonNode configured = schema.get(Tool.BODY_VALUE_KEY);
        String key = configured != null && configured.isTextual() ? configured.asText() : name;
        return switch (schema.get(Tool.BODY_SOURCE_KEY).asText()) {
            case "static" -> configured;
            case "variable" -> jsonNode(callVariables == null ? null : callVariables.get(key));
            case "secret" -> secrets.containsKey(key) ? TextNode.valueOf(secrets.get(key)) : null;
            default -> null;
        };
    }

    /** A call variable as JSON: numbers and booleans bare, anything else — a date, a name — as text. */
    private static JsonNode jsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return MAPPER.valueToTree(value);
        }
        return TextNode.valueOf(value.toString());
    }

    public static String sanitizeToolName(String name) {
        if (name == null || name.isBlank()) {
            return "custom_tool";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * A conversation that is not there: the panel's test run. The tool may still say its
     * pre-tool line and record an outcome; neither has anywhere to go.
     */
    private static final class DryRunSink implements DialogOutcomeSink {

        @Override
        public String channelId() {
            return "tool-test";
        }

        @Override
        public void addToolReply(String reply) {
        }

        @Override
        public ScenarioDefinition scenario() {
            return null;
        }

        @Override
        public String state() {
            return null;
        }

        @Override
        public void setState(String state) {
        }

        @Override
        public void recordOutcome(String key, Object value) {
        }

        @Override
        public void setDisposition(Disposition disposition) {
        }

        @Override
        public void end(Disposition disposition) {
        }

        @Override
        public void setDoNotCallReason(String reason) {
        }

        @Override
        public boolean sendDtmf(String digits) {
            return false;
        }
    }
}
