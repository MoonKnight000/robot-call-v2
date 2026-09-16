package uz.murodjon.robotcallv2.callflow.domain.entity;

import java.time.Instant;

/**
 * A call flow (docs/INBOUND-PBX-PLAN.md §4). Edits land in {@code draftGraph}; calls run
 * {@code publishedGraph}, which only a successful publish replaces.
 *
 * @param publishedGraph what calls run; null until first published
 * @param version        how many times it has been published
 */
public record CallFlow(
        long id,
        long companyId,
        String name,
        String description,
        CallFlowGraph draftGraph,
        CallFlowGraph publishedGraph,
        int version,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean published() {
        return publishedGraph != null;
    }

    /** Whether the draft differs from what calls are running. */
    public boolean unpublishedChanges() {
        return publishedGraph == null || !publishedGraph.equals(draftGraph);
    }
}
