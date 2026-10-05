package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Fills a company tool's configured URL, header values and body with this call's values
 * ({@link HttpToolExecutor}).
 *
 * <p>Secrets are always resolved <em>first</em>: after that a call variable or a model
 * argument that happens to read {@code {{secrets.X}}} is just text, so nothing the caller
 * or the model supplied can make the request fetch a credential.
 */
@Component
final class ToolRequestTemplates {

    private final ObjectMapper objectMapper;

    /**
     * The body parameter whose value is the whole request body rather than one field of
     * it. See {@link #findRawBodyTemplate}.
     */
    private static final String RAW_BODY_PARAM = "__raw__";

    ToolRequestTemplates(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The tool's URL with its path parameters substituted and its query parameters appended. */
    String resolveUrl(Tool tool, Map<String, Object> callVariables, Map<String, Object> args,
                             Map<String, String> secrets) {
        // Secrets first: after this line the string holds credentials, and a path or query
        // value that happens to contain a placeholder is just text.
        String url = SecretPlaceholders.resolve(tool.apiUrl(), secrets);
        List<ToolParamConfig> pathParams = tool.apiPathParams();
        if (pathParams != null) {
            for (ToolParamConfig param : pathParams) {
                Object value = resolveParamValue(param, callVariables, args);
                String pathValue = value != null ? value.toString() : "";
                url = url.replace("{" + param.name() + "}", URLEncoder.encode(pathValue, StandardCharsets.UTF_8));
            }
        }

        StringBuilder queryString = new StringBuilder();
        List<ToolParamConfig> queryParams = tool.apiQueryParams();
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

    /** A header value (or any plain-text template) with secrets, call variables and arguments substituted. */
    String resolveTemplate(String template, Map<String, Object> callVariables, Map<String, Object> args,
                                  Map<String, String> secrets) {
        if (template == null) {
            return "";
        }
        // A call variable or a model argument reading "{{secrets.CRM_TOKEN}}" must stay that
        // text, not become the token.
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

    /** The request body: from the tool's {@code bodySchema} when it has one, else from its body parameters. */
    String buildBody(Tool tool, Map<String, Object> callVariables, Map<String, Object> args,
                            Map<String, String> secrets) {
        return tool.bodySchema().isEmpty()
                ? buildParamBody(tool.apiBody(), callVariables, args, secrets)
                : buildSchemaBody(tool.bodySchema(), callVariables, args, secrets);
    }

    /**
     * The request body: one field per configured parameter, unless the tool supplied a
     * whole body itself through {@link #RAW_BODY_PARAM}.
     */
    private String buildParamBody(List<ToolParamConfig> bodyParams, Map<String, Object> callVariables,
                                         Map<String, Object> args, Map<String, String> secrets) {
        String template = findRawBodyTemplate(bodyParams);
        if (template != null) {
            return renderRawBody(template, callVariables, args, secrets);
        }
        ObjectNode node = objectMapper.createObjectNode();
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
    private static String findRawBodyTemplate(List<ToolParamConfig> bodyParams) {
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
                body = body.replace("{{" + variable.getKey() + "}}", toJsonText(variable.getValue()));
            }
        }
        if (args != null) {
            for (Map.Entry<String, Object> argument : args.entrySet()) {
                body = body.replace("{{" + argument.getKey() + "}}", toJsonText(argument.getValue()));
            }
        }
        return body;
    }

    /** A value as it may appear inside a JSON document, without its surrounding quotes. */
    private String toJsonText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        try {
            String quoted = objectMapper.writeValueAsString(value.toString());
            return quoted.substring(1, quoted.length() - 1);
        } catch (Exception e) {
            return "";
        }
    }

    private static Object resolveParamValue(ToolParamConfig config, Map<String, Object> callVariables,
                                            Map<String, Object> args) {
        return switch (config.valueType()) {
            case STATIC_VALUE -> config.value();
            case DYNAMIC_VARIABLE -> {
                String variableName = config.value() != null ? config.value().toString() : config.name();
                yield callVariables != null ? callVariables.get(variableName) : null;
            }
            case LLM_PROMPT -> args != null ? args.get(config.name()) : null;
        };
    }

    /**
     * The request body a {@code bodySchema} describes: nested objects and arrays exactly as
     * the model sent them where the schema leaves a field to it, and the
     * {@value Tool#BODY_SOURCE_KEY} fields filled from the configuration, the call or a secret.
     * Only the fields the schema names go out — an argument the model made up is not sent —
     * and a secret comes from the schema alone, never from an argument.
     */
    String buildSchemaBody(Map<String, Object> schema, Map<String, Object> callVariables,
                                  Map<String, Object> args, Map<String, String> secrets) {
        JsonNode body = fill(objectMapper.valueToTree(schema), objectMapper.valueToTree(args == null ? Map.of() : args), null,
                callVariables, secrets);
        return body == null ? "{}" : body.toString();
    }

    private JsonNode fill(JsonNode schema, JsonNode given, String name,
                                 Map<String, Object> callVariables, Map<String, String> secrets) {
        if (schema.has(Tool.BODY_SOURCE_KEY)) {
            return findSourcedValue(schema, name, callVariables, secrets);
        }
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            return given; // an array, a scalar or a free-form object: as the model sent it
        }
        ObjectNode filled = objectMapper.createObjectNode();
        properties.fields().forEachRemaining(field -> {
            JsonNode child = given != null && given.isObject() ? given.get(field.getKey()) : null;
            JsonNode value = fill(field.getValue(), child, field.getKey(), callVariables, secrets);
            if (value != null && !value.isNull()) {
                filled.set(field.getKey(), value);
            }
        });
        return filled.isEmpty() && given == null ? null : filled;
    }

    private JsonNode findSourcedValue(JsonNode schema, String name, Map<String, Object> callVariables,
                                             Map<String, String> secrets) {
        JsonNode configured = schema.get(Tool.BODY_VALUE_KEY);
        String key = configured != null && configured.isTextual() ? configured.asText() : name;
        return switch (schema.get(Tool.BODY_SOURCE_KEY).asText()) {
            case "static" -> configured;
            case "variable" -> toJsonNode(callVariables == null ? null : callVariables.get(key));
            case "secret" -> secrets.containsKey(key) ? TextNode.valueOf(secrets.get(key)) : null;
            default -> null;
        };
    }

    /** A call variable as JSON: numbers and booleans bare, anything else — a date, a name — as text. */
    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return objectMapper.valueToTree(value);
        }
        return TextNode.valueOf(value.toString());
    }
}
