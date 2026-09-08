package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    private final HttpClient httpClient;
    private final SecretUseCase secretUseCase;

    public HttpToolExecutor(SecretUseCase secretUseCase) {
        this.secretUseCase = secretUseCase;
        // Redirects are never followed: PublicUrlGuard screens the address that was
        // configured, and a 302 is the endpoint picking a second one after that check has
        // passed — which is exactly how such a filter is walked around.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ToolCallback buildCallback(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables) {
        String toolName = sanitizeToolName(tool.name());
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolName, args -> execute(tool, session, callVariables, args))
                .description(tool.description())
                .inputSchema(generateSchema(tool))
                .inputType(Map.class)
                .build();
    }

    public String execute(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables, Map<String, Object> args) {
        if (tool.forcePreToolSpeech() && tool.preToolSpeech() != null && !tool.preToolSpeech().isBlank()) {
            session.addToolReply(tool.preToolSpeech().trim());
        }

        Map<String, String> secrets = tool.companyId() != null
                ? secretUseCase.findDecryptedSecrets(tool.companyId())
                : Map.of();

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

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            // Apply Headers
            boolean hasContentType = false;
            if (tool.apiHeaders() != null) {
                for (ToolKeyValuePair header : tool.apiHeaders()) {
                    if (header.key() != null && !header.key().isBlank()) {
                        String headerValue = resolveTemplate(header.value(), callVariables, args, secrets);
                        requestBuilder.header(header.key().trim(), headerValue);
                        if ("content-type".equalsIgnoreCase(header.key().trim())) {
                            hasContentType = true;
                        }
                    }
                }
            }

            // Build Body
            HttpRequest.BodyPublisher bodyPublisher;
            if ("GET".equals(method) || "DELETE".equals(method)) {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            } else {
                if (!hasContentType) {
                    requestBuilder.header("Content-Type", "application/json");
                }
                String jsonBody = buildBody(tool.apiBody(), callVariables, args, secrets);
                bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
            }

            requestBuilder.method(method, bodyPublisher);

            log.info("[{}] Executing HTTP tool '{}' {} {}", session.channelId(), tool.name(), method,
                    SecretPlaceholders.redact(resolvedUrl, secrets.values()));
            HttpResponse<InputStream> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            log.info("[{}] Tool '{}' responded with status {}", session.channelId(), tool.name(), response.statusCode());

            String responseBody = readCapped(response.body());
            session.recordOutcome("tool_" + tool.name(), Map.of(
                    "status", response.statusCode(),
                    "response", responseBody
            ));

            ObjectNode resultNode = MAPPER.createObjectNode();
            resultNode.put("status", response.statusCode());
            try {
                JsonNode parsedJson = MAPPER.readTree(responseBody);
                resultNode.set("data", parsedJson);
            } catch (Exception notJson) {
                resultNode.put("data", responseBody);
            }
            String result = MAPPER.writeValueAsString(resultNode);
            if (result.length() <= MAX_RESPONSE_CHARS_FOR_MODEL) {
                return result;
            }
            log.warn("[{}] Tool '{}' answered with {} chars — truncated for the model",
                    session.channelId(), tool.name(), result.length());
            ObjectNode truncatedNode = MAPPER.createObjectNode();
            truncatedNode.put("status", response.statusCode());
            truncatedNode.put("data", responseBody.substring(0,
                    Math.min(responseBody.length(), MAX_RESPONSE_CHARS_FOR_MODEL)));
            truncatedNode.put("truncated", true);
            return MAPPER.writeValueAsString(truncatedNode);

        } catch (Exception e) {
            // The detail stays on the server. A connection or URI failure names the address
            // it failed on, and that address has the credential in it by this point — the
            // model has no use for it and repeats what it is told out loud.
            log.error("[{}] Tool '{}' execution failed: {}", session.channelId(), tool.name(),
                    SecretPlaceholders.redact(String.valueOf(e.getMessage()), secrets.values()));
            ObjectNode errorNode = MAPPER.createObjectNode();
            errorNode.put("status", 500);
            errorNode.put("error", "Tool execution failed");
            return errorNode.toString();
        }
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

    /** Reads at most {@link #MAX_RESPONSE_BYTES}; the rest of the stream is dropped unread. */
    private static String readCapped(InputStream body) throws IOException {
        try (InputStream stream = body) {
            return new String(stream.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
        }
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

    public static String sanitizeToolName(String name) {
        if (name == null || name.isBlank()) {
            return "custom_tool";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
