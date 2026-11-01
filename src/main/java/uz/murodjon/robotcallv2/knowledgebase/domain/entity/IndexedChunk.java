package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.util.Set;

/**
 * A chunk as the search sees it: the passage, its vector, the document it came from, and
 * who may use that document — everything needed to filter, score and attribute a hit
 * without going back to the database.
 *
 * @param agentId      null when the source is company-wide, i.e. every agent may use it
 * @param collectionId the named knowledge base the source sits in, or null; such a source is used
 *                     only by the agents attached to that collection
 */
public record IndexedChunk(
        long sourceId,
        Long agentId,
        String sourceName,
        String content,
        float[] embedding,
        Long collectionId
) {

    /** A chunk of a source outside any collection. */
    public IndexedChunk(long sourceId, Long agentId, String sourceName, String content, float[] embedding) {
        this(sourceId, agentId, sourceName, content, embedding, null);
    }

    /**
     * Whether this passage is one the given agent is allowed to answer from.
     *
     * @param attachedCollectionIds the collections attached to {@code callAgentId}
     */
    public boolean visibleTo(Long callAgentId, Set<Long> attachedCollectionIds) {
        if (callAgentId == null) {
            return true;
        }
        if (collectionId != null) {
            return attachedCollectionIds.contains(collectionId);
        }
        return agentId == null || agentId.equals(callAgentId);
    }
}
