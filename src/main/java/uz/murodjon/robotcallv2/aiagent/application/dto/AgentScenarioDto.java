package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

import java.util.List;

/**
 * Behavior and embedded Scenario configuration for an AI Agent.
 */
public record AgentScenarioDto(
        ScenarioMode scenarioMode,
        String firstMessage,
        String systemPrompt,
        List<StageDef> stages,
        List<String> guardrails,
        List<FactField> factSchema,
        String disclosureText
) {
    public ScenarioMode scenarioModeOrDefault() {
        return scenarioMode != null ? scenarioMode : ScenarioMode.PROMPT;
    }

    public List<StageDef> stagesOrEmpty() {
        return stages != null ? stages : List.of();
    }

    public List<String> guardrailsOrEmpty() {
        return guardrails != null ? guardrails : List.of();
    }

    public List<FactField> factSchemaOrEmpty() {
        return factSchema != null ? factSchema : List.of();
    }
}
