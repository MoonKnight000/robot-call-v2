package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;
import java.util.Map;

public record ScenarioSimulationResponse(
        String assistantReply,
        String nextState,
        String calledTool,
        Map<String, Object> toolArguments,
        Map<String, Object> recordedOutcome,
        String disposition,
        boolean ended,
        long latencyMs
) {
}
