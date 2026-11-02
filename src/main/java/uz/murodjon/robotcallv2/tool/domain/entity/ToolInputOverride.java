package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolInputSource;

/**
 * A new source for one input of a tool, by the input's name ({@link ToolInput}).
 *
 * @param value       the fixed value for {@code VALUE}, the variable's name for {@code VARIABLE}; ignored for {@code AI}
 * @param description for {@code AI}: what the model is told about the input; null keeps the tool's own
 */
public record ToolInputOverride(
        String name,
        ToolInputSource source,
        Object value,
        String description
) {
    public ToolInputOverride {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
