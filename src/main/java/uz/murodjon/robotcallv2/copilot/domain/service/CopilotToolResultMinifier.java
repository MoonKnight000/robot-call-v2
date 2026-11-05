package uz.murodjon.robotcallv2.copilot.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * Minifies and compresses raw tool JSON results before returning them to the LLM.
 *
 * <p>Removes nulls, strips deep technical boilerplate, caps large array items (e.g. in
 * {@code list_calls}), and bounds character length. This prevents raw data dumps from
 * eating thousands of prompt tokens and keeping the model focused on high-signal attributes.
 */
public final class CopilotToolResultMinifier {

    public static final int MAX_MINIFIED_CHARS = 8_000;
    private static final int MAX_ARRAY_ITEMS = 12;
    private static final ObjectMapper JSON = new ObjectMapper();

    private CopilotToolResultMinifier() {
    }

    /**
     * Minifies the tool response string if it is valid JSON, or safely truncates it.
     *
     * @param toolName  name of the executed MCP tool
     * @param rawAnswer raw output string from the tool handler
     * @return clean, minified, high-density text for the model
     */
    public static String minify(String toolName, String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return "{}";
        }
        String trimmed = rawAnswer.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return truncate(trimmed);
        }

        try {
            JsonNode root = JSON.readTree(trimmed);
            JsonNode cleaned = cleanNode(root);
            String compactJson = JSON.writeValueAsString(cleaned);
            return truncate(compactJson);
        } catch (Exception ignored) {
            // If parsing fails, fall back to safe character truncation
            return truncate(trimmed);
        }
    }

    private static JsonNode cleanNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode val = entry.getValue();
                if (val.isNull()) {
                    fields.remove();
                } else if (val.isObject() || val.isArray()) {
                    cleanNode(val);
                }
            }
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            if (arr.size() > MAX_ARRAY_ITEMS) {
                int total = arr.size();
                while (arr.size() > MAX_ARRAY_ITEMS) {
                    arr.remove(arr.size() - 1);
                }
                ObjectNode notice = JSON.createObjectNode();
                notice.put("_note", "Truncated for brevity; %d total items exist".formatted(total));
                arr.add(notice);
            }
            for (int i = 0; i < arr.size(); i++) {
                cleanNode(arr.get(i));
            }
            return arr;
        }
        return node;
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_MINIFIED_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MINIFIED_CHARS) + "\n…[cut: output shortened to save context tokens]";
    }
}
