package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

public record ScenarioDefinition(
        List<StageDef> stages,
        List<FactField> factSchema,
        List<ToolDef> tools,
        List<OutcomeField> outcomeSchema,
        String rolePrompt,
        List<String> guardrails,
        String disclosureText
) {
}
