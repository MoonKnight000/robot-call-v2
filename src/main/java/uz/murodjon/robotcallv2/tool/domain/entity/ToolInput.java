package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolInputSource;

/**
 * One input of a tool's request as it is configured: a path, query or body parameter, or a top-level
 * field of its {@code bodySchema}. An input filled from a secret is never listed.
 *
 * @param type        the JSON type the endpoint expects ({@code string}, {@code integer}, {@code array} …); may be null
 * @param value       the fixed value for {@code VALUE}, the variable's name for {@code VARIABLE}, null for {@code AI}
 * @param description what the model is told about it; meaningful for {@code AI}
 */
public record ToolInput(
        String name,
        String type,
        ToolInputSource source,
        Object value,
        String description
) {
}
