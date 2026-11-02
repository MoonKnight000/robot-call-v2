package uz.murodjon.robotcallv2.tool.domain.entity;

import java.util.List;
import java.util.Map;

/**
 * What one user of a shared tool changes about it for themselves — an agent's own settings for an
 * integration tool, as Retell's function form has them: where each input comes from, and which fields of
 * the answer reach the model.
 *
 * @param inputs          inputs given a new source; one the tool does not have is ignored
 * @param responseMapping replaces the tool's {@code responseMapping} — the fields the model sees, each also a
 *                        variable of the call under its name; null or empty keeps the tool's
 */
public record ToolOverride(
        List<ToolInputOverride> inputs,
        Map<String, String> responseMapping
) {
    public ToolOverride {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        responseMapping = responseMapping == null || responseMapping.isEmpty() ? null : Map.copyOf(responseMapping);
    }
}
