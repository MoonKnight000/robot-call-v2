package uz.murodjon.robotcallv2.callflow.domain.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The nodes of a call flow and where a call starts. Stored as one JSON document, so the
 * flow editor writes the whole graph at once and a published version is a copy.
 *
 * @param entry id of the first node
 * @param nodes node id → node
 */
public record CallFlowGraph(
        String entry,
        Map<String, CallFlowNode> nodes
) {
    public CallFlowGraph {
        nodes = nodes == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    public CallFlowNode node(String id) {
        return id == null ? null : nodes.get(id);
    }
}
