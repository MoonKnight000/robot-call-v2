package uz.murodjon.robotcallv2.scenario.application.dto;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.util.List;
import java.util.Map;

public record ScenarioSimulationRequest(
        Long scenarioId,
        ScenarioDefinition scenarioDefinition,
        String userMessage,
        String currentState,
        List<Map<String, String>> chatHistory,
        Map<String, Object> contextFacts
) {
}
