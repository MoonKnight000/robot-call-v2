package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentScript;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

/**
 * {@link AiAgentScript} as the agent endpoints read and write it. On a write a null field
 * keeps what the agent has (or the create default).
 */
public record AgentScriptDto(
        AgentTemplate templateId,
        Long scenarioId,
        ScenarioMode mode,
        ScenarioDefinition definition,
        String firstMessage,
        String systemPrompt
) {

    public static AgentScriptDto of(AiAgentScript script) {
        return new AgentScriptDto(script.templateId(), script.scenarioId(), script.mode(), script.definition(),
                script.firstMessage(), script.systemPrompt());
    }
}
