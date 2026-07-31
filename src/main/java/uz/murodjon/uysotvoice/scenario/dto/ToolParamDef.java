package uz.murodjon.uysotvoice.scenario.dto;

/**
 * One argument of a {@link ToolDef}.
 *
 * @param name       argument name, as the LLM must call the tool with it
 * @param type       {@code "string"}, {@code "number"}, {@code "date"}, or {@code "boolean"}
 * @param required   whether the tool call is invalid without it
 * @param constraint free-text rule folded into the tool's description (e.g. "must be a
 *                   future date") — not machine-checked, the model is simply told
 */
public record ToolParamDef(String name, String type, boolean required, String constraint) {
}
