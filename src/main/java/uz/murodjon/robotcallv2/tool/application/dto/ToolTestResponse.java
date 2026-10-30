package uz.murodjon.robotcallv2.tool.application.dto;

import java.util.Map;

/**
 * What a tool's dry run returned.
 *
 * @param result exactly what the model would be handed: {@code status} and {@code data} —
 *               mapped by {@code responseMapping} when the tool has one, made prompt-safe,
 *               and {@code truncated: true} when it had to be cut
 * @param raw    the endpoint's answer as it came, before any mapping — what a person reads to
 *               pick the fields the model should see; a JSON value, or text when it was not JSON;
 *               null when the request got no answer
 * @param mapped what the request's {@code responseMapping} picks out of {@code raw}, made
 *               prompt-safe; null when none was sent or {@code raw} is not JSON
 */
public record ToolTestResponse(Map<String, Object> result, Object raw, Object mapped) {
}
