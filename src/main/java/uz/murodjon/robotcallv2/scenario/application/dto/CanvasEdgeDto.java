package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.Map;

/**
 * A directed edge connecting two canvas nodes (React Flow / Rete.js compatible).
 *
 * @param id unique edge identifier (e.g. "e_greeting_qualification")
 * @param source source node id
 * @param target target node id
 * @param label human-readable condition or branch rule text displayed on the edge
 * @param type edge category: "transition", "branch", "tool", "default"
 * @param data additional edge parameters (e.g. branch operator, fact name, target condition)
 */
public record CanvasEdgeDto(
        String id,
        String source,
        String target,
        String label,
        String type,
        Map<String, Object> data
) {
}
