package uz.murodjon.robotcallv2.tool.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.*;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolAuthType;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.net.URI;
import java.util.*;

/**
 * Turns an OpenAPI 3 document into company tools (UNIVERSAL-OPERATOR-PLAN B.2): path and query
 * parameters become the model's arguments, a JSON body becomes the tool's {@code bodySchema},
 * and the security scheme becomes headers, an address or an OAuth client reading the company's
 * secrets.
 *
 * <p>Only what is inside the document is read. A {@code $ref} to another file is not followed —
 * fetching it would be a request to an address nobody vetted — and its schema is left open.
 * An imported tool is an ordinary tool: whatever the document did not say (a response mapping,
 * a readback) is added by hand afterwards.
 */
public final class OpenApiToolImporter {

    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");
    /** Deep enough for a real schema, short of looping on one that refers to itself. */
    private static final int MAX_REF_DEPTH = 12;
    private static final int MAX_NAME_CHARS = 100;
    private static final int MAX_DESCRIPTION_CHARS = 500;
    private static final int MAX_URL_CHARS = 1000;
    private static final int MAX_PREFIX_CHARS = 30;
    private static final String FALLBACK_SECRET_PREFIX = "OPENAPI";

    private OpenApiToolImporter() {
    }

    /**
     * Where the tools send their requests: {@code baseUrl} when given, else the document's first
     * server with its variables at their defaults — resolved against the document's own address
     * when it is relative. Null when that leaves no absolute address.
     */
    public static String findServerUrl(Map<String, Object> document, String baseUrl, String documentUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return trimSlash(baseUrl.trim());
        }
        String server = null;
        if (document.get("servers") instanceof List<?> servers && !servers.isEmpty()
                && servers.getFirst() instanceof Map<?, ?> first) {
            server = textOf(first.get("url"));
            if (server != null && first.get("variables") instanceof Map<?, ?> variables) {
                for (Map.Entry<?, ?> variable : variables.entrySet()) {
                    if (variable.getValue() instanceof Map<?, ?> spec && spec.get("default") != null) {
                        server = server.replace("{" + variable.getKey() + "}", spec.get("default").toString());
                    }
                }
            }
        }
        if (server == null || server.isBlank()) {
            server = "/"; // what OpenAPI means by a document without servers
        }
        if (server.startsWith("http://") || server.startsWith("https://")) {
            return trimSlash(server);
        }
        if (documentUrl == null || documentUrl.isBlank()) {
            return null;
        }
        try {
            return trimSlash(URI.create(documentUrl.trim()).resolve(server).toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Every operation of the document, in its order, each with its tool or the reason it has none.
     *
     * @param serverUrl    where the tools send their requests; empty only for a listing
     * @param secretPrefix the start of the secret keys the tools read; null derives it from the title
     */
    public static List<OpenApiOperation> readOperations(Map<String, Object> document, String serverUrl,
                                                        String secretPrefix) {
        Object version = document.get("openapi");
        if (version == null || !version.toString().startsWith("3.")) {
            Object found = version != null ? version
                    : document.get("swagger") != null ? "Swagger " + document.get("swagger") : "no version";
            throw new ValidationException(ErrorCode.OPENAPI_VERSION_UNSUPPORTED, found);
        }
        String prefix = secretPrefixOf(secretPrefix, document);
        List<OpenApiOperation> operations = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        if (!(document.get("paths") instanceof Map<?, ?> paths)) {
            return operations;
        }
        for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
            if (!(pathEntry.getValue() instanceof Map<?, ?> pathItem)) {
                continue;
            }
            String path = String.valueOf(pathEntry.getKey());
            List<?> pathParameters = listOf(pathItem.get("parameters"));
            for (Map.Entry<?, ?> methodEntry : pathItem.entrySet()) {
                String method = String.valueOf(methodEntry.getKey()).toLowerCase(Locale.ROOT);
                if (METHODS.contains(method) && methodEntry.getValue() instanceof Map<?, ?> operation) {
                    operations.add(readOperation(document, serverUrl, prefix, path, method, operation,
                            pathParameters, usedNames));
                }
            }
        }
        return operations;
    }

    private static OpenApiOperation readOperation(Map<String, Object> document, String serverUrl, String prefix,
                                                  String path, String method, Map<?, ?> operation,
                                                  List<?> pathParameters, Set<String> usedNames) {
        String operationId = textOf(operation.get("operationId"));
        if (operationId == null || operationId.isBlank()) {
            operationId = method + path.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+$", "");
        }
        String summary = textOf(operation.get("summary"));

        List<ToolParamConfig> pathParams = new ArrayList<>();
        List<ToolParamConfig> queryParams = new ArrayList<>();
        for (Map<?, ?> parameter : parametersOf(document, pathParameters, listOf(operation.get("parameters")))) {
            String in = textOf(parameter.get("in"));
            String name = textOf(parameter.get("name"));
            if (name == null || !("path".equals(in) || "query".equals(in))) {
                continue; // a header or a cookie parameter is the configuration's, not the model's
            }
            Map<?, ?> schema = resolveRef(document, parameter.get("schema"), 0) instanceof Map<?, ?> found ? found : Map.of();
            ToolParamConfig config = new ToolParamConfig(name, parameterType(schema), ToolParamValueType.LLM_PROMPT,
                    null, textOf(parameter.get("description")), enumOf(schema),
                    "path".equals(in) || Boolean.TRUE.equals(parameter.get("required")));
            ("path".equals(in) ? pathParams : queryParams).add(config);
        }

        String unsupported = null;
        Map<String, Object> bodySchema = Map.of();
        if (resolveRef(document, operation.get("requestBody"), 0) instanceof Map<?, ?> body
                && body.get("content") instanceof Map<?, ?> content && !content.isEmpty()) {
            Object media = jsonMedia(content);
            if (media == null) {
                unsupported = "the request body is not JSON";
            } else {
                Map<String, Object> schema = schemaOf(document, media instanceof Map<?, ?> m ? m.get("schema") : null, 0);
                if (schema.get("properties") instanceof Map) {
                    schema.put("type", "object");
                    bodySchema = schema;
                } else {
                    unsupported = "the request body is not a JSON object";
                }
            }
        }

        List<ToolKeyValuePair> headers = new ArrayList<>();
        List<String> secretKeys = new ArrayList<>();
        String apiUrl = serverUrl + path;
        ToolAuth auth = null;
        Map.Entry<Map<?, ?>, List<?>> security = securityOf(document, operation);
        if (security != null) {
            Map<?, ?> scheme = security.getKey();
            String type = String.valueOf(scheme.get("type"));
            switch (type) {
                case "http" -> {
                    boolean basic = "basic".equalsIgnoreCase(textOf(scheme.get("scheme")));
                    String key = prefix + (basic ? "_BASIC_AUTH" : "_TOKEN");
                    headers.add(header("Authorization", (basic ? "Basic " : "Bearer ") + secret(key)));
                    secretKeys.add(key);
                }
                case "apiKey" -> {
                    String key = prefix + "_API_KEY";
                    String name = textOf(scheme.get("name"));
                    switch (String.valueOf(scheme.get("in"))) {
                        case "query" -> apiUrl += "?" + name + "=" + secret(key);
                        case "cookie" -> headers.add(header("Cookie", name + "=" + secret(key)));
                        default -> headers.add(header(name, secret(key)));
                    }
                    secretKeys.add(key);
                }
                case "oauth2" -> {
                    if (scheme.get("flows") instanceof Map<?, ?> flows
                            && flows.get("clientCredentials") instanceof Map<?, ?> flow
                            && textOf(flow.get("tokenUrl")) != null) {
                        List<String> scopes = security.getValue().stream().map(String::valueOf).toList();
                        auth = new ToolAuth(ToolAuthType.OAUTH2_CLIENT_CREDENTIALS,
                                absolute(textOf(flow.get("tokenUrl")), serverUrl),
                                prefix + "_CLIENT_ID", prefix + "_CLIENT_SECRET",
                                scopes.isEmpty() ? null : String.join(" ", scopes));
                        secretKeys.add(prefix + "_CLIENT_ID");
                        secretKeys.add(prefix + "_CLIENT_SECRET");
                    } else {
                        bearerToken(prefix, headers, secretKeys);
                    }
                }
                default -> bearerToken(prefix, headers, secretKeys);
            }
        }
        if (unsupported == null && apiUrl.length() > MAX_URL_CHARS) {
            unsupported = "the address is longer than " + MAX_URL_CHARS + " characters";
        }

        Tool tool = unsupported != null ? null : new Tool(null, null,
                uniqueName(operationId, usedNames),
                descriptionOf(summary, textOf(operation.get("description")), method, path),
                new ToolHttpConfig(apiUrl, method.toUpperCase(Locale.ROOT), headers, List.of(), queryParams,
                        pathParams, bodySchema, null, 0, auth, null, null),
                List.of(), new ToolSpeech(false, false, null), Map.of(), Tool.DEFAULT_MAX_CALLS_PER_CALL,
                effectOf(method), null, null, null, null);
        return new OpenApiOperation(operationId, method.toUpperCase(Locale.ROOT), path, summary, tool,
                List.copyOf(secretKeys), unsupported);
    }

    /** The path item's parameters overlaid with the operation's, which win on the same name and place. */
    private static List<Map<?, ?>> parametersOf(Map<String, Object> document, List<?> pathLevel, List<?> operationLevel) {
        Map<String, Map<?, ?>> byPlace = new LinkedHashMap<>();
        for (List<?> level : List.of(pathLevel, operationLevel)) {
            for (Object parameter : level) {
                if (resolveRef(document, parameter, 0) instanceof Map<?, ?> resolved) {
                    byPlace.put(resolved.get("in") + ":" + resolved.get("name"), resolved);
                }
            }
        }
        return new ArrayList<>(byPlace.values());
    }

    /** {@code application/json}, or any {@code +json} media type; null when the body is something else. */
    private static Object jsonMedia(Map<?, ?> content) {
        for (Map.Entry<?, ?> entry : content.entrySet()) {
            String type = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            if (type.startsWith("application/json") || type.contains("+json")) {
                return entry.getValue() == null ? Map.of() : entry.getValue();
            }
        }
        return null;
    }

    /**
     * The security scheme the operation authenticates with — its own requirement, else the
     * document's, the first one listed — with the scopes it asks for. Null for none, and for a
     * requirement that is empty ({@code {}}), which means authentication is optional.
     */
    private static Map.Entry<Map<?, ?>, List<?>> securityOf(Map<String, Object> document, Map<?, ?> operation) {
        Object requirements = operation.containsKey("security") ? operation.get("security") : document.get("security");
        if (!(requirements instanceof List<?> list) || list.isEmpty()
                || !(list.getFirst() instanceof Map<?, ?> requirement) || requirement.isEmpty()) {
            return null;
        }
        Map.Entry<?, ?> first = requirement.entrySet().iterator().next();
        Object schemes = document.get("components") instanceof Map<?, ?> components ? components.get("securitySchemes") : null;
        Object scheme = schemes instanceof Map<?, ?> byName ? resolveRef(document, byName.get(first.getKey()), 0) : null;
        return scheme instanceof Map<?, ?> found ? Map.entry(found, listOf(first.getValue())) : null;
    }

    private static void bearerToken(String prefix, List<ToolKeyValuePair> headers, List<String> secretKeys) {
        headers.add(header("Authorization", "Bearer " + secret(prefix + "_TOKEN")));
        secretKeys.add(prefix + "_TOKEN");
    }

    /**
     * A schema as a tool's {@code bodySchema}: every local {@code $ref} inlined, {@code allOf}
     * merged, {@code oneOf}/{@code anyOf} taken at their first choice, and {@code readOnly}
     * fields — the ones the server fills, like an id — left out.
     */
    private static Map<String, Object> schemaOf(Map<String, Object> document, Object node, int depth) {
        Object resolved = resolveRef(document, node, depth);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(resolved instanceof Map<?, ?> schema) || depth > MAX_REF_DEPTH) {
            result.put("type", "object");
            return result;
        }
        if (schema.get("allOf") instanceof List<?> parts) {
            parts.forEach(part -> mergeInto(result, schemaOf(document, part, depth + 1)));
        }
        for (String choice : List.of("oneOf", "anyOf")) {
            if (schema.get(choice) instanceof List<?> options && !options.isEmpty()) {
                mergeInto(result, schemaOf(document, options.getFirst(), depth + 1));
            }
        }
        for (Map.Entry<?, ?> entry : schema.entrySet()) {
            String keyword = String.valueOf(entry.getKey());
            switch (keyword) {
                case "$ref", "allOf", "oneOf", "anyOf", "properties", "required" -> {
                }
                case "items" -> result.put("items", schemaOf(document, entry.getValue(), depth + 1));
                default -> result.put(keyword, entry.getValue());
            }
        }
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (result.get("properties") instanceof Map<?, ?> earlier) {
                earlier.forEach((name, field) -> merged.put(String.valueOf(name), field));
            }
            for (Map.Entry<?, ?> property : properties.entrySet()) {
                Map<String, Object> field = schemaOf(document, property.getValue(), depth + 1);
                if (!Boolean.TRUE.equals(field.get("readOnly"))) {
                    merged.put(String.valueOf(property.getKey()), field);
                }
            }
            result.put("properties", merged);
        }
        List<String> required = new ArrayList<>(listOf(result.get("required")).stream().map(String::valueOf).toList());
        listOf(schema.get("required")).forEach(name -> {
            if (!required.contains(String.valueOf(name))) {
                required.add(String.valueOf(name));
            }
        });
        if (result.get("properties") instanceof Map<?, ?> kept) {
            required.removeIf(name -> !kept.containsKey(name));
        }
        if (required.isEmpty()) {
            result.remove("required");
        } else {
            result.put("required", required);
        }
        return result;
    }

    private static void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((keyword, value) -> {
            if ("properties".equals(keyword) && value instanceof Map<?, ?> properties) {
                Map<String, Object> merged = new LinkedHashMap<>();
                if (target.get("properties") instanceof Map<?, ?> earlier) {
                    earlier.forEach((name, field) -> merged.put(String.valueOf(name), field));
                }
                properties.forEach((name, field) -> merged.put(String.valueOf(name), field));
                target.put("properties", merged);
            } else if ("required".equals(keyword)) {
                List<Object> merged = new ArrayList<>(listOf(target.get("required")));
                listOf(value).forEach(name -> {
                    if (!merged.contains(name)) {
                        merged.add(name);
                    }
                });
                target.put("required", merged);
            } else {
                target.putIfAbsent(keyword, value);
            }
        });
    }

    /** The node a local {@code $ref} ({@code #/components/...}) points at, or the node itself; null for another file's. */
    private static Object resolveRef(Map<String, Object> document, Object node, int depth) {
        Object current = node;
        for (int hops = depth; current instanceof Map<?, ?> map && map.get("$ref") instanceof String ref; hops++) {
            if (!ref.startsWith("#/") || hops > MAX_REF_DEPTH) {
                return null;
            }
            Object target = document;
            for (String segment : ref.substring(2).split("/")) {
                String name = segment.replace("~1", "/").replace("~0", "~");
                target = target instanceof Map<?, ?> container ? container.get(name) : null;
            }
            current = target;
        }
        return current;
    }

    private static String parameterType(Map<?, ?> schema) {
        String type = textOf(schema.get("type"));
        return type != null && Set.of("string", "integer", "number", "boolean").contains(type) ? type : "string";
    }

    private static List<String> enumOf(Map<?, ?> schema) {
        return listOf(schema.get("enum")).stream().map(String::valueOf).toList();
    }

    /** A read stays a read; a delete is never offered to the model (B.3); everything else writes. */
    private static ToolEffect effectOf(String method) {
        return switch (method) {
            case "get" -> ToolEffect.READ;
            case "delete" -> ToolEffect.DESTRUCTIVE;
            default -> ToolEffect.WRITE;
        };
    }

    private static String descriptionOf(String summary, String description, String method, String path) {
        StringBuilder text = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            text.append(summary.strip());
        }
        if (description != null && !description.isBlank() && !description.strip().equals(text.toString())) {
            text.append(text.isEmpty() ? "" : ". ").append(description.strip());
        }
        String result = text.isEmpty() ? method.toUpperCase(Locale.ROOT) + " " + path : text.toString();
        return result.length() <= MAX_DESCRIPTION_CHARS ? result : result.substring(0, MAX_DESCRIPTION_CHARS);
    }

    /** The operation id as a function name; a second one that comes out the same gets {@code _2}. */
    private static String uniqueName(String operationId, Set<String> usedNames) {
        String base = operationId.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() > MAX_NAME_CHARS - 4) {
            base = base.substring(0, MAX_NAME_CHARS - 4);
        }
        String name = base;
        for (int n = 2; !usedNames.add(name); n++) {
            name = base + "_" + n;
        }
        return name;
    }

    /** The given prefix, else the document's title, as a secret key start: {@code Shop API} → {@code SHOP_API}. */
    private static String secretPrefixOf(String secretPrefix, Map<String, Object> document) {
        String source = secretPrefix;
        if ((source == null || source.isBlank()) && document.get("info") instanceof Map<?, ?> info) {
            source = textOf(info.get("title"));
        }
        String prefix = source == null ? "" : source.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (prefix.length() > MAX_PREFIX_CHARS) {
            prefix = prefix.substring(0, MAX_PREFIX_CHARS).replaceAll("_+$", "");
        }
        return prefix.isEmpty() ? FALLBACK_SECRET_PREFIX : prefix;
    }

    private static String absolute(String url, String serverUrl) {
        if (url.startsWith("http://") || url.startsWith("https://") || serverUrl == null || serverUrl.isBlank()) {
            return url;
        }
        try {
            return URI.create(serverUrl + "/").resolve(url).toString();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static ToolKeyValuePair header(String name, String value) {
        return new ToolKeyValuePair(name, value, null, null);
    }

    private static String secret(String key) {
        return "{{secrets." + key + "}}";
    }

    private static String textOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
