package uz.murodjon.robotcallv2.tool.application.dto;

import java.util.Map;

/**
 * What a tool's dry run returned.
 *
 * @param result exactly what the model would be handed: {@code status} and {@code data} —
 *               mapped by {@code responseMapping} when the tool has one, made prompt-safe,
 *               and {@code truncated: true} when it had to be cut
 */
public record ToolTestResponse(Map<String, Object> result) {
}
