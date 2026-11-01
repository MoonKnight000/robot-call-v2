package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * An integration tool a company has on, as the settings screen lists it.
 *
 * @param key      the catalog key ({@code CREATE_LEAD})
 * @param toolName the name the model calls an HTTP tool by; null for a built-in tool
 */
public record AgentIntegrationTool(String key, String toolName) {

    /** The name a function refers to the tool by: its {@code toolName}, else its key. */
    public String listedName() {
        return toolName != null ? toolName : key;
    }
}
