package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;
import java.util.Map;

public record ScenarioSimulationRequest(
        Long scenarioId,
        ScenarioDefinition scenarioDefinition,
        String userMessage,
        String currentState,
        List<Map<String, String>> chatHistory, // [{role: "user"|"assistant", content: "..."}]
        Map<String, Object> contextFacts,
        String language
) {
}
