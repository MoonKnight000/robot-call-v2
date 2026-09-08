package uz.murodjon.robotcallv2.tool.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a tool parameter's value comes from when the call is about to be made.
 *
 * <p>{@link #from(String)} accepts the labels the configuration screen used to send as
 * free text ("Static Value", "Dynamic Variable", "LLM Prompt") so tools configured before
 * this became an enum keep working; anything unrecognised is treated as {@link
 * #LLM_PROMPT}, which is what an unset value has always meant.
 */
public enum ToolParamValueType {

    /** Written once in the tool's configuration and sent unchanged on every call. */
    STATIC_VALUE("Static Value"),

    /** Read from the call's own variables — the phone number, a CRM fact, a fetched value. */
    DYNAMIC_VARIABLE("Dynamic Variable"),

    /** Asked of the model: the parameter is published in the tool's schema and filled per call. */
    LLM_PROMPT("LLM Prompt");

    private final String label;

    ToolParamValueType(String label) {
        this.label = label;
    }

    /** The screen's own wording, kept so stored configurations stay readable. */
    @JsonValue
    public String label() {
        return label;
    }

    /** Never throws: an unknown or missing value is a parameter the model fills in. */
    @JsonCreator
    public static ToolParamValueType from(String value) {
        if (value == null || value.isBlank()) {
            return LLM_PROMPT;
        }
        String normalized = value.trim();
        for (ToolParamValueType type : values()) {
            if (type.name().equalsIgnoreCase(normalized) || type.label.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return LLM_PROMPT;
    }
}
