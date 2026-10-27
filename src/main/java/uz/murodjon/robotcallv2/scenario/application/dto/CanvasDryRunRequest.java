package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.Map;

/**
 * Request to simulate a single step/turn on the visual canvas studio.
 *
 * @param scenarioId scenario ID to simulate (optional if customCanvas is provided)
 * @param currentNodeId current active node ID (e.g. "greeting")
 * @param userInput caller utterance or simulated response
 * @param facts current call facts (e.g. {"debtAmount": 150000, "clientName": "Ali"})
 * @param currentOutcomes outcomes gathered so far
 * @param customCanvas unsaved draft canvas graph to test in-memory without saving
 */
public record CanvasDryRunRequest(
        Long scenarioId,
        String currentNodeId,
        String userInput,
        Map<String, Object> facts,
        Map<String, Object> currentOutcomes,
        CanvasGraphDto customCanvas
) {
}
