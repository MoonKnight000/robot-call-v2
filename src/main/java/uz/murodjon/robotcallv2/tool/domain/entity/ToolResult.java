package uz.murodjon.robotcallv2.tool.domain.entity;

import java.util.Map;

/**
 * How a tool's answer reaches the model, and what the call keeps from it.
 *
 * @param maxItems          how many elements of each list in the answer the model is shown; 0 shows them all
 * @param emptyMessage      what the model is told instead of an answer with nothing in it (an empty list or
 *                          object); null passes the empty answer through
 * @param errorMessage      what the model is told when the request fails or answers outside 2xx; null tells it
 *                          only that the tool failed
 * @param argumentVariables argument the model filled → the call variable its value is saved under once the
 *                          tool answered 2xx ({@code rooms → roomsNeeded}); a list is saved joined by ", "
 * @param memoryFacts       {@code responseMapping} field → the client-memory fact it is saved as once the tool
 *                          answered 2xx ({@code flats → offeredFlats}), so the next call knows what was offered
 */
public record ToolResult(
        int maxItems,
        String emptyMessage,
        String errorMessage,
        Map<String, String> argumentVariables,
        Map<String, String> memoryFacts
) {
    public static final ToolResult NONE = new ToolResult(0, null, null, null, null);

    public ToolResult {
        maxItems = Math.max(0, maxItems);
        emptyMessage = emptyMessage == null || emptyMessage.isBlank() ? null : emptyMessage.trim();
        errorMessage = errorMessage == null || errorMessage.isBlank() ? null : errorMessage.trim();
        argumentVariables = argumentVariables == null ? Map.of() : Map.copyOf(argumentVariables);
        memoryFacts = memoryFacts == null ? Map.of() : Map.copyOf(memoryFacts);
    }
}
