package uz.murodjon.robotcallv2.tool.domain.service;

import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInput;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInputOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolInputSource;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tool's inputs as one list ({@link #findInputs}), and the tool as a {@link ToolOverride} changes it
 * ({@link #apply}). An input is a path, query or body parameter, or a top-level field of the
 * {@code bodySchema}; one filled from a secret is neither listed nor changed.
 */
public final class ToolOverrides {

    private static final String SECRET_SOURCE = "secret";

    private ToolOverrides() {
    }

    /** The tool's inputs in request order: path, query, body parameters, then the body schema's fields. */
    public static List<ToolInput> findInputs(Tool tool) {
        ToolHttpConfig http = tool.http();
        List<ToolInput> inputs = new ArrayList<>();
        for (List<ToolParamConfig> params : List.of(http.pathParams(), http.queryParams(), http.body())) {
            for (ToolParamConfig param : params) {
                inputs.add(new ToolInput(param.name(), param.type(), sourceOf(param.valueType()),
                        param.valueType() == ToolParamValueType.LLM_PROMPT ? null : param.value(), param.description()));
            }
        }
        for (Map.Entry<String, Map<?, ?>> field : schemaFields(http.bodySchema()).entrySet()) {
            Map<?, ?> schema = field.getValue();
            Object source = schema.get(Tool.BODY_SOURCE_KEY);
            if (SECRET_SOURCE.equals(source)) {
                continue;
            }
            Object type = schema.get("type");
            Object description = schema.get("description");
            ToolInputSource inputSource = "static".equals(source) ? ToolInputSource.VALUE
                    : "variable".equals(source) ? ToolInputSource.VARIABLE : ToolInputSource.AI;
            // A variable field without a value reads the variable of its own name (ToolRequestTemplates).
            Object value = switch (inputSource) {
                case AI -> null;
                case VALUE -> schema.get(Tool.BODY_VALUE_KEY);
                case VARIABLE -> schema.containsKey(Tool.BODY_VALUE_KEY) ? schema.get(Tool.BODY_VALUE_KEY) : field.getKey();
            };
            inputs.add(new ToolInput(field.getKey(), type instanceof String text ? text : null, inputSource, value,
                    description instanceof String text ? text : null));
        }
        return inputs;
    }

    /** {@code tool} with {@code override}'s inputs and answer fields; the tool itself when there is nothing to change. */
    public static Tool apply(Tool tool, ToolOverride override) {
        if (override == null || override.inputs().isEmpty() && override.responseMapping() == null) {
            return tool;
        }
        Map<String, ToolInputOverride> byName = new HashMap<>();
        override.inputs().forEach(input -> byName.put(input.name(), input));
        ToolHttpConfig http = tool.http();
        ToolHttpConfig changed = new ToolHttpConfig(http.url(), http.method(), http.headers(),
                applyToParams(http.body(), byName), applyToParams(http.queryParams(), byName),
                applyToParams(http.pathParams(), byName), applyToSchema(http.bodySchema(), byName),
                http.responseTimeoutSecs(), http.retryAttempts(), http.auth(), http.waitForResult(), http.statusVariable());
        return new Tool(tool.id(), tool.companyId(), tool.name(), tool.description(), changed, tool.dynamicVariables(),
                tool.speech(), override.responseMapping() != null ? override.responseMapping() : tool.responseMapping(),
                tool.result(), tool.maxCallsPerCall(), tool.effect(), tool.requiresConfirmation(),
                tool.readbackTemplate(), tool.createdAt(), tool.updatedAt());
    }

    /**
     * {@code tool} with its "wait for result" set to {@code waitForResult} — one user of a shared tool deciding
     * whether its turn waits for the answer; the tool itself when it already says so.
     */
    public static Tool withWaitForResult(Tool tool, boolean waitForResult) {
        ToolHttpConfig http = tool.http();
        if (http.waitForResult() == waitForResult) {
            return tool;
        }
        ToolHttpConfig changed = new ToolHttpConfig(http.url(), http.method(), http.headers(), http.body(),
                http.queryParams(), http.pathParams(), http.bodySchema(), http.responseTimeoutSecs(),
                http.retryAttempts(), http.auth(), waitForResult, http.statusVariable());
        return new Tool(tool.id(), tool.companyId(), tool.name(), tool.description(), changed, tool.dynamicVariables(),
                tool.speech(), tool.responseMapping(), tool.result(), tool.maxCallsPerCall(), tool.effect(),
                tool.requiresConfirmation(), tool.readbackTemplate(), tool.createdAt(), tool.updatedAt());
    }

    private static List<ToolParamConfig> applyToParams(List<ToolParamConfig> params, Map<String, ToolInputOverride> byName) {
        List<ToolParamConfig> changed = new ArrayList<>(params.size());
        for (ToolParamConfig param : params) {
            ToolInputOverride input = byName.get(param.name());
            if (input == null || input.source() == null) {
                changed.add(param);
                continue;
            }
            changed.add(switch (input.source()) {
                case AI -> new ToolParamConfig(param.name(), param.type(), ToolParamValueType.LLM_PROMPT, null,
                        input.description() != null ? input.description() : param.description(),
                        param.allowedValues(), param.required());
                case VALUE -> new ToolParamConfig(param.name(), param.type(), ToolParamValueType.STATIC_VALUE,
                        input.value(), param.description(), param.allowedValues(), param.required());
                case VARIABLE -> new ToolParamConfig(param.name(), param.type(), ToolParamValueType.DYNAMIC_VARIABLE,
                        input.value(), param.description(), param.allowedValues(), param.required());
            });
        }
        return changed;
    }

    /** The schema with its overridden top-level fields re-sourced; copied only where it changes. */
    private static Map<String, Object> applyToSchema(Map<String, Object> schema, Map<String, ToolInputOverride> byName) {
        Map<String, Map<?, ?>> fields = schemaFields(schema);
        if (fields.isEmpty() || byName.keySet().stream().noneMatch(fields::containsKey)) {
            return schema;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        fields.forEach((name, field) -> {
            ToolInputOverride input = byName.get(name);
            properties.put(name, input == null || input.source() == null || SECRET_SOURCE.equals(field.get(Tool.BODY_SOURCE_KEY))
                    ? field : resource(field, input));
        });
        Map<String, Object> changed = new LinkedHashMap<>(schema);
        changed.put("properties", properties);
        return changed;
    }

    private static Map<String, Object> resource(Map<?, ?> field, ToolInputOverride input) {
        Map<String, Object> copy = new LinkedHashMap<>();
        field.forEach((key, value) -> copy.put(String.valueOf(key), value));
        copy.remove(Tool.BODY_SOURCE_KEY);
        copy.remove(Tool.BODY_VALUE_KEY);
        switch (input.source()) {
            case AI -> {
                if (input.description() != null) {
                    copy.put("description", input.description());
                }
            }
            case VALUE -> {
                copy.put(Tool.BODY_SOURCE_KEY, "static");
                copy.put(Tool.BODY_VALUE_KEY, input.value());
            }
            case VARIABLE -> {
                copy.put(Tool.BODY_SOURCE_KEY, "variable");
                copy.put(Tool.BODY_VALUE_KEY, input.value());
            }
        }
        return copy;
    }

    /** The schema's top-level fields that are objects, in order; empty for a schema with none. */
    private static Map<String, Map<?, ?>> schemaFields(Map<String, Object> schema) {
        Map<String, Map<?, ?>> fields = new LinkedHashMap<>();
        if (schema != null && schema.get("properties") instanceof Map<?, ?> properties) {
            properties.forEach((name, field) -> {
                if (field instanceof Map<?, ?> fieldSchema) {
                    fields.put(String.valueOf(name), fieldSchema);
                }
            });
        }
        return fields;
    }

    private static ToolInputSource sourceOf(ToolParamValueType valueType) {
        return switch (valueType) {
            case STATIC_VALUE -> ToolInputSource.VALUE;
            case DYNAMIC_VARIABLE -> ToolInputSource.VARIABLE;
            case LLM_PROMPT -> ToolInputSource.AI;
        };
    }
}
