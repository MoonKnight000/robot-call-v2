package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The arguments a company tool asks the model for, as the JSON Schema of its function declaration. */
@Component
final class ToolSchemas {

    private final ObjectMapper objectMapper;

    /** The JSON Schema keywords a function declaration takes; anything else in a bodySchema stays out of it. */
    private static final Set<String> MODEL_SCHEMA_KEYWORDS = Set.of("type", "description", "properties",
            "required", "items", "enum", "format", "minimum", "maximum", "minItems", "maxItems", "nullable");

    ToolSchemas(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Every parameter the model fills — path, query, body fields and a nested body's fields — as one object schema. */
    String generateInputSchema(Tool tool) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        collectLlmParams(tool.http().pathParams(), properties, required);
        collectLlmParams(tool.http().queryParams(), properties, required);
        collectLlmParams(tool.http().body(), properties, required);
        // A nested body: its fields are the model's top-level arguments, less the ones the
        // configuration fills (UNIVERSAL-OPERATOR-PLAN B.1).
        if (!tool.http().bodySchema().isEmpty()) {
            JsonNode body = schemaForModel(objectMapper.valueToTree(tool.http().bodySchema()));
            body.path("properties").fields().forEachRemaining(field -> properties.set(field.getKey(), field.getValue()));
            body.path("required").forEach(required::add);
        }

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    private static void collectLlmParams(List<ToolParamConfig> configs, ObjectNode properties, ArrayNode required) {
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
     * The part of a {@code bodySchema} the model fills (UNIVERSAL-OPERATOR-PLAN B.1). A field
     * whose value comes from the configuration, the call or a secret ({@value Tool#BODY_SOURCE_KEY})
     * is left out, and so is every keyword a function declaration does not take — a model
     * provider refuses a declaration it cannot read.
     */
    JsonNode schemaForModel(JsonNode schema) {
        ObjectNode copy = objectMapper.createObjectNode();
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
}
