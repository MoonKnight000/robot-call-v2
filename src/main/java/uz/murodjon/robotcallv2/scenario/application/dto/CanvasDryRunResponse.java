package uz.murodjon.robotcallv2.scenario.application.dto;

import java.util.List;
import java.util.Map;

/**
 * Result of a single step/turn execution on the visual canvas studio.
 *
 * @param previousNodeId node where turn started
 * @param nextNodeId node where turn transitioned to (or stayed at)
 * @param activeEdgeId visual edge traversed during this transition, if any
 * @param botResponse what the bot says (rendered with fact substitution)
 * @param transitionReason explanation of why the transition took place (branch rule, LLM condition, global interrupt)
 * @param updatedFacts modified facts after step
 * @param updatedOutcomes recorded outcomes after step
 * @param terminal true if next node is a conversation end stage (no outgoing transitions)
 * @param visitedPath sequence of node IDs traversed in this step
 * @param availableTransitions outgoing transition options from the next node
 */
public record CanvasDryRunResponse(
        String previousNodeId,
        String nextNodeId,
        String activeEdgeId,
        String botResponse,
        String transitionReason,
        Map<String, Object> updatedFacts,
        Map<String, Object> updatedOutcomes,
        boolean terminal,
        List<String> visitedPath,
        List<String> availableTransitions
) {
}
