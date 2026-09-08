package uz.murodjon.robotcallv2.mcp.domain.service;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;

import java.util.Collection;
import java.util.List;

/**
 * Which of a server's tools the model is allowed to know about.
 *
 * <p>The model is on a live phone call with a stranger who can say anything, so the rule
 * is about what a talked-into model could do, not about what the tool is for:
 *
 * <ul>
 *   <li>A tool the server marks <b>destructive</b> is never offered. There is no wording
 *       of "are you sure" that survives a determined caller, and undoing a deletion at
 *       somebody else's system is not something this platform can do.</li>
 *   <li>A tool the server marks <b>read-only</b> is always offered.</li>
 *   <li>Everything else — a tool that writes but does not destroy, like booking a slot —
 *       is offered only when the company switched {@code allowWrites} on for that
 *       connection.</li>
 * </ul>
 *
 * <p>Filtering happens before the prompt is built, not at call time: a tool the model was
 * never told about cannot be argued into being called.
 */
public final class McpToolGuard {

    private McpToolGuard() {
    }

    public static List<McpTool> filterUsable(Collection<McpTool> discovered, boolean allowWrites) {
        if (discovered == null || discovered.isEmpty()) {
            return List.of();
        }
        return discovered.stream()
                .filter(tool -> tool != null && tool.name() != null && !tool.name().isBlank())
                .filter(tool -> !tool.destructive())
                .filter(tool -> tool.readOnly() || allowWrites)
                .toList();
    }
}
