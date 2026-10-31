package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

/**
 * A tool a scenario declares for itself: a call records each parameter as an outcome field.
 *
 * @param disposition the {@code Disposition} a successful call gives the conversation (e.g.
 *                    {@code PROMISE_TO_PAY}), or null when the tool only records values
 */
public record ToolDef(
        String name,
        String description,
        List<ToolParamDef> params,
        String preToolSpeech,
        String disposition
) {
    public ToolDef(String name, String description, List<ToolParamDef> params) {
        this(name, description, params, null, null);
    }
}
