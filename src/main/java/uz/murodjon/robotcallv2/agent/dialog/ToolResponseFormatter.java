package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the model is told after a company tool ran ({@link HttpToolExecutor}).
 *
 * <p>The answer is text this system did not write (NEXT-PLAN A.4): only the fields a tool's
 * {@code responseMapping} names reach the model when it has one, and every text value goes
 * through {@link PromptSafeText} on the way.
 */
@Component
final class ToolResponseFormatter {

    private static final Logger log = LoggerFactory.getLogger(ToolResponseFormatter.class);
    private final ObjectMapper objectMapper;

    /**
     * How much of the answer reaches the model. Everything past this is prompt the model pays
     * for and cannot use; a tool that needs more than this is answering the wrong question.
     */
    private static final int MAX_RESPONSE_CHARS_FOR_MODEL = 4000;

    ToolResponseFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The status and the answer — only the mapped fields when the tool has a
     * {@code responseMapping} — with every text value made prompt-safe, then cut to
     * {@link #MAX_RESPONSE_CHARS_FOR_MODEL}. A successful answer also leaves its findings on
     * the call ({@link #recordFindings}).
     */
    String formatForModel(Tool tool, DialogOutcomeSink session, int status, String responseBody)
            throws IOException {
        JsonNode data;
        try {
            data = objectMapper.readTree(responseBody);
        } catch (Exception notJson) {
            data = null;
        }
        if (data == null || data.isMissingNode()) {
            data = TextNode.valueOf(responseBody);
        } else if (!tool.responseMapping().isEmpty()) {
            data = applyMapping(data, tool.responseMapping());
        }
        data = sanitizeForPrompt(data);
        if (status >= 200 && status < 300) {
            recordFindings(tool, session, data);
        }

        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("status", status);
        resultNode.set("data", data);
        String result = objectMapper.writeValueAsString(resultNode);
        if (result.length() <= MAX_RESPONSE_CHARS_FOR_MODEL) {
            return result;
        }
        log.warn("[{}] Tool '{}' answered with {} chars — truncated for the model",
                session.channelId(), tool.name(), result.length());
        String dataText = data.isTextual() ? data.asText() : objectMapper.writeValueAsString(data);
        ObjectNode truncatedNode = objectMapper.createObjectNode();
        truncatedNode.put("status", status);
        truncatedNode.put("data", dataText.substring(0, Math.min(dataText.length(), MAX_RESPONSE_CHARS_FOR_MODEL)));
        truncatedNode.put("truncated", true);
        return objectMapper.writeValueAsString(truncatedNode);
    }

    /** What the model is told once a tool has been called as often as one call allows. */
    String formatLimitReached(int limit) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", 429);
        node.put("error", "This action has already been done " + limit + " times on this call. Do not call it again; "
                + "carry on the conversation with the result you have.");
        return node.toString();
    }

    /**
     * What the model is told when the address was refused. Deliberately the same shape as
     * a failure: the model's job is to carry on the conversation without the tool, not to
     * learn which addresses this server declines to call.
     */
    String formatBlocked() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", 400);
        node.put("error", "Tool execution failed");
        return node.toString();
    }

    /** What the model is told when the request did not go through; the reason stays on the server. */
    String formatFailed() {
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("status", 500);
        errorNode.put("error", "Tool execution failed");
        return errorNode.toString();
    }

    /**
     * What a successful answer leaves on the call. The mapped fields become the call's
     * outcome under their mapping names — how a company tool reaches a scenario's goal
     * ({@code orderId} from {@code create_order}, UNIVERSAL-OPERATOR-PLAN A.1) — and every
     * figure the model was shown becomes one it may say out loud ({@link FactGuard}): a price
     * the catalog answered with is given, not invented. Only a 2xx answer counts, because an
     * error body can echo back the very figure the model sent.
     */
    private void recordFindings(Tool tool, DialogOutcomeSink session, JsonNode data) {
        if (!tool.responseMapping().isEmpty() && data.isObject()) {
            data.fields().forEachRemaining(field ->
                    session.recordOutcome(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class)));
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
    JsonNode applyMapping(JsonNode root, Map<String, String> mapping) {
        ObjectNode mapped = objectMapper.createObjectNode();
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
    String toPointer(String path) {
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
    private JsonNode sanitizeForPrompt(JsonNode node) {
        if (node.isTextual()) {
            String safe = PromptSafeText.sanitize(node.asText(), MAX_RESPONSE_CHARS_FOR_MODEL);
            return safe == null ? NullNode.getInstance() : TextNode.valueOf(safe);
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.fields().forEachRemaining(field -> copy.set(field.getKey(), sanitizeForPrompt(field.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(element -> copy.add(sanitizeForPrompt(element)));
            return copy;
        }
        return node;
    }
}
