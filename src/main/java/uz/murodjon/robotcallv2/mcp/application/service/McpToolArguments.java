package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a tool's arguments strictly: an unknown field is refused, so a misspelt one is not
 * silently dropped, and a converted request is checked with its own bean-validation constraints.
 * Every problem is {@code MCP_TOOL_ARGUMENTS_INVALID}, which the model reads and corrects.
 */
public final class McpToolArguments {

    private final ObjectMapper json = McpServerToolCatalog.newMapper();
    private final Validator validator;

    public McpToolArguments(Validator validator) {
        this.validator = validator;
    }

    public ObjectMapper mapper() {
        return json;
    }

    public <T> T convert(String tool, JsonNode args, Class<T> type) {
        T value;
        try {
            value = json.treeToValue(args, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            String reason = e instanceof JsonProcessingException parse ? parse.getOriginalMessage() : e.getMessage();
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(reason), tool);
        }
        List<String> problems = validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .toList();
        if (!problems.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, problems, tool);
        }
        return value;
    }

    /** A model writes an id as a number or as digits in a string; both are taken. */
    public long requireId(String tool, JsonNode args, String field) {
        Long id = optionalId(tool, args, field);
        if (id == null) {
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(field + " is required"), tool);
        }
        return id;
    }

    public Long optionalId(String tool, JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && value.asText().matches("\\d{1,18}")) {
            return Long.parseLong(value.asText());
        }
        throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(field + " must be an id"), tool);
    }

    public List<Long> optionalIds(String tool, JsonNode args, String field) {
        JsonNode value = args.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of(field + " must be a list of ids"), tool);
        }
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < value.size(); i++) {
            ids.add(requireId(tool, value, i));
        }
        return ids;
    }

    public Integer optionalInt(JsonNode args, String field) {
        JsonNode value = args.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    public String optionalText(JsonNode args, String field) {
        JsonNode value = args.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private long requireId(String tool, JsonNode array, int index) {
        JsonNode value = array.get(index);
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && value.asText().matches("\\d{1,18}")) {
            return Long.parseLong(value.asText());
        }
        throw ValidationException.withErrors(ErrorCode.MCP_TOOL_ARGUMENTS_INVALID, List.of("ids must be numbers"), tool);
    }
}
