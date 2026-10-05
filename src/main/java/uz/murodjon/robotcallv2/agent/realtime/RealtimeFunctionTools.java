package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * The OpenAI-style {@code tools} array — {@code {type: function, name, description, parameters}}
 * — that the OpenAI, Qwen-Omni and Pipecat engines are offered their tools in. The schema is
 * the cascade pipeline's own {@link ToolCallback} schema, reused verbatim. Gemini Live
 * declares tools in its own shape and does not use this.
 */
final class RealtimeFunctionTools {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFunctionTools.class);

    private RealtimeFunctionTools() {
    }

    /** Adds {@code tools} under {@code parent.tools}; nothing is added when there are none. */
    static void putFunctionTools(ObjectMapper objectMapper, ObjectNode parent, List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        ArrayNode toolArray = parent.putArray("tools");
        for (ToolCallback tool : tools) {
            ObjectNode declaration = toolArray.addObject();
            declaration.put("type", "function");
            declaration.put("name", tool.getToolDefinition().name());
            declaration.put("description", tool.getToolDefinition().description());
            try {
                declaration.set("parameters", objectMapper.readTree(tool.getToolDefinition().inputSchema()));
            } catch (Exception e) {
                // Still offered, only without a schema the engine can check arguments against.
                log.debug("Tool {} has an unreadable input schema: {}", tool.getToolDefinition().name(), e.getMessage());
            }
        }
    }
}
