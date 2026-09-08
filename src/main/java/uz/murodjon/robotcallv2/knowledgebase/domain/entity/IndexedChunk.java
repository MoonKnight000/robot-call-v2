package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * A chunk as the search sees it: the passage, its vector, the document it came from, and
 * the agent that document belongs to — everything needed to filter, score and attribute a
 * hit without going back to the database.
 *
 * @param agentId null when the source is company-wide, i.e. every agent may use it
 */
public record IndexedChunk(
        long sourceId,
        Long agentId,
        String sourceName,
        String content,
        float[] embedding
) {
    /** Whether this passage is one the given agent is allowed to answer from. */
    public boolean visibleTo(Long callAgentId) {
        return agentId == null || callAgentId == null || agentId.equals(callAgentId);
    }
}
