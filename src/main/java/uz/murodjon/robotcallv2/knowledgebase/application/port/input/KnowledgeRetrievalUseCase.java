package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.util.List;

/**
 * What the dialog asks the knowledge base during a call: the passages that bear on what
 * the caller just said, so the model can answer from the company's own documents instead
 * of from what it happens to believe.
 */
public interface KnowledgeRetrievalUseCase {

    /**
     * The best {@code limit} passages for the question, strongest first, or an empty list
     * when nothing is relevant enough to be worth putting in the prompt.
     *
     * <p>Never throws and never blocks on anything slower than one embedding call: a
     * caller is waiting on the far end, so an unreachable model or an empty index means no
     * passages, not a failed turn.
     *
     * @param agentId the call's agent; passages of other agents' sources are excluded,
     *                company-wide ones are always included
     */
    List<KnowledgePassage> findRelevantPassages(long companyId, Long agentId, String question, int limit);

    /**
     * Drops whatever this company's passages were cached as, so the next call reads them
     * again. Called by whoever changed the knowledge base — indexing a source, deleting
     * one — because a call must not answer from text the operator has already removed.
     */
    void evictCompany(long companyId);
}
