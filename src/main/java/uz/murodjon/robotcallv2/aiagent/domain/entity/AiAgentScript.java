package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

/**
 * What the agent says: the opening line, the role prompt, and where the rest of the
 * conversation comes from.
 *
 * <p>{@link ScenarioMode#PROMPT} means the systemPrompt is the whole script;
 * a staged mode means {@code definition} (embedded) or {@code scenarioId} (a shared
 * scenario row) carries it. Grouped because every edit that touches one of these touches
 * the others — switching modes without the matching text produces an agent that opens a
 * call with nothing to say.
 *
 * @param templateId    the preset the agent was seeded from; never null, {@link AgentTemplate#BLANK} when none
 * @param scenarioId    a shared scenario this agent speaks, or null when the script is its own
 * @param mode          how the script is expressed; never null
 * @param definition    the embedded scenario, when the agent carries its own
 * @param firstMessage  the line the agent opens with
 * @param systemPrompt  the role prompt handed to the model
 */
public record AiAgentScript(
        AgentTemplate templateId,
        Long scenarioId,
        ScenarioMode mode,
        ScenarioDefinition definition,
        String firstMessage,
        String systemPrompt
) {
    public AiAgentScript {
        if (templateId == null) {
            templateId = AgentTemplate.BLANK;
        }
        if (mode == null) {
            mode = ScenarioMode.PROMPT;
        }
    }

    /** The same script with a different opening line. */
    public AiAgentScript withFirstMessage(String newFirstMessage) {
        return new AiAgentScript(templateId, scenarioId, mode, definition, newFirstMessage, systemPrompt);
    }
}
