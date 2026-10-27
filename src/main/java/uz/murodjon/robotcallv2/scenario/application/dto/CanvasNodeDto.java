package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.Map;

/**
 * A visual node on the scenario canvas (React Flow / Rete.js compatible).
 *
 * @param id unique node identifier (e.g. stage id, global node id)
 * @param type node category: "start", "stage", "global_node", "tool", "outcome"
 * @param position 2D canvas coordinates
 * @param data arbitrary node properties and configuration attributes
 */
public record CanvasNodeDto(
        String id,
        String type,
        CanvasPositionDto position,
        Map<String, Object> data
) {
}
