package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.List;

/**
 * Full visual canvas graph representation of a Scenario, ready for React Flow / Vue Flow / Rete.js.
 *
 * @param scenarioId scenario database identifier
 * @param scenarioKey slug / unique key of the scenario
 * @param name human-readable name of the scenario
 * @param version version number (used for optimistic concurrency locking)
 * @param nodes visual canvas nodes
 * @param edges visual canvas directed edges
 * @param viewport canvas camera pan and zoom
 * @param validationErrors structural validation errors if any
 * @param valid true if scenario graph passes all structural and FSM integrity checks
 */
public record CanvasGraphDto(
        Long scenarioId,
        String scenarioKey,
        String name,
        Integer version,
        List<CanvasNodeDto> nodes,
        List<CanvasEdgeDto> edges,
        CanvasViewportDto viewport,
        List<String> validationErrors,
        boolean valid
) {
}
