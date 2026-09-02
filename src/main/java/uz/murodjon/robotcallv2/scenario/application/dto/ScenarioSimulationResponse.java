package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.Map;

public record ScenarioSimulationResponse(
        String assistantReply,
        String nextState,
        String calledTool,
        Map<String, Object> toolArguments,
        Map<String, Object> extractedFacts,
        String disposition,
        boolean ended,
        long latencyMs
) {
}
