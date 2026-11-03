package uz.murodjon.robotcallv2.tool.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;

import java.util.*;
import java.util.regex.Pattern;

public final class ToolValidator {

    /**
     * Request field → entity property: the only columns a tool list sorts by. Looked up
     * without case or underscores, so both {@code CREATED_AT} (the documented spelling)
     * and {@code createdAt} work.
     */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdat", "createdAt",
            "updatedat", "updatedAt",
            "name", "name");
    private static final List<String> SORTABLE_FIELD_NAMES = List.of("createdAt", "updatedAt", "name");

    private static final Set<String> BODY_SOURCES = Set.of("static", "variable", "secret");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private ToolValidator() {
    }

    /**
     * A tool's body is described one way or the other (UNIVERSAL-OPERATOR-PLAN B.1): the flat
     * {@code apiBody} list, or a JSON Schema for a body with nested objects and arrays. Given
     * both, which one is sent would be a guess.
     */
    public static void validateBody(List<ToolParamConfig> apiBody, Map<String, Object> bodySchema) {
        if (bodySchema == null || bodySchema.isEmpty()) {
            return;
        }
        if (apiBody != null && !apiBody.isEmpty()) {
            throw new ValidationException(ErrorCode.TOOL_BODY_AMBIGUOUS);
        }
        if (!"object".equals(bodySchema.get("type")) || !(bodySchema.get("properties") instanceof Map)) {
            throw new ValidationException(ErrorCode.TOOL_BODY_SCHEMA_ROOT_INVALID);
        }
        checkBodyField(bodySchema, "$", false);
    }

    /**
     * A {@value Tool#BODY_SOURCE_KEY} field is filled once per request, so it cannot sit
     * inside array items: the model fills every item, and the source would have nowhere to go.
     */
    private static void checkFallback(Map<?, ?> schema, String path) {
        Object fallback = schema.get(Tool.BODY_FALLBACK_KEY);
        if (fallback != null) {
            List<?> names = fallback instanceof List<?> list ? list : List.of(fallback);
            if (names.isEmpty() || !names.stream().allMatch(name -> name instanceof String text
                    && VARIABLE_NAME.matcher(text).matches())) {
                throw new ValidationException(ErrorCode.TOOL_BODY_FALLBACK_INVALID, path);
            }
        }
    }

    private static void checkBodyField(Map<?, ?> schema, String path, boolean insideArray) {
        Object source = schema.get(Tool.BODY_SOURCE_KEY);
        if (source != null) {
            if (insideArray) {
                throw new ValidationException(ErrorCode.TOOL_BODY_SOURCE_IN_ARRAY, path);
            }
            if (!BODY_SOURCES.contains(String.valueOf(source))) {
                throw new ValidationException(ErrorCode.TOOL_BODY_SOURCE_INVALID, path, source);
            }
            // A variable field may name fallbacks too; a static or secret one has nothing to fall back from.
            if ("variable".equals(source)) {
                checkFallback(schema, path);
            } else if (schema.get(Tool.BODY_FALLBACK_KEY) != null) {
                throw new ValidationException(ErrorCode.TOOL_BODY_FALLBACK_INVALID, path);
            }
            return;
        }
        checkFallback(schema, path);
        Object format = schema.get(Tool.BODY_FORMAT_KEY);
        if (format != null && !Tool.FORMAT_EPOCH_SECONDS.equals(format)) {
            throw new ValidationException(ErrorCode.TOOL_BODY_FORMAT_INVALID, path, format);
        }
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            properties.forEach((name, field) -> {
                if (field instanceof Map<?, ?> fieldSchema) {
                    checkBodyField(fieldSchema, path + "." + name, insideArray);
                }
            });
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            checkBodyField(items, path + "[]", true);
        }
    }

    /**
     * A tool that gets its own access token (UNIVERSAL-OPERATOR-PLAN B.5) names where from and
     * which of the company's secrets hold the client's id and secret — never the values.
     */
    public static void validateAuth(ToolAuth auth) {
        if (auth == null) {
            return;
        }
        if (auth.type() == null) {
            throw new ValidationException(ErrorCode.TOOL_AUTH_TYPE_REQUIRED);
        }
        if (auth.tokenUrl() == null || auth.tokenUrl().isBlank()) {
            throw new ValidationException(ErrorCode.TOOL_AUTH_TOKEN_URL_REQUIRED);
        }
        if (auth.clientIdSecret() == null || auth.clientIdSecret().isBlank()) {
            throw new ValidationException(ErrorCode.TOOL_AUTH_SECRET_REQUIRED, "clientIdSecret");
        }
        if (auth.clientSecretSecret() == null || auth.clientSecretSecret().isBlank()) {
            throw new ValidationException(ErrorCode.TOOL_AUTH_SECRET_REQUIRED, "clientSecretSecret");
        }
    }

    /**
     * The requested sort with each field spelled as the entity property. An unknown field
     * is a 400 here — passed on raw it reached Spring Data, which answered with a 500.
     */
    public static Map<String, String> validateSortFields(Map<String, String> orders) {
        Map<String, String> sortable = new LinkedHashMap<>();
        orders.forEach((field, direction) -> {
            String property = field == null ? null
                    : SORTABLE_FIELDS.get(field.replace("_", "").toLowerCase(Locale.ROOT));
            if (property == null) {
                throw new ValidationException(ErrorCode.SORT_FIELD_UNKNOWN, field, SORTABLE_FIELD_NAMES);
            }
            sortable.put(property, direction);
        });
        return sortable;
    }
}
