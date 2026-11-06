package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeQuery;

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
     * {@link #findRelevantPassages} over the agent's passages plus those of {@code extraSourceIds}
     * — the sources a scenario stage adds for its own turns, even when they belong to another
     * agent of the company. Sources of another company are never read.
     */
    List<KnowledgePassage> findRelevantPassagesIncludingSources(long companyId, Long agentId, List<Long> extraSourceIds,
                                                                String question, int limit);

    /**
     * The general search the other two narrow: {@code query.question()} drives keyword matching and
     * the minimum-length rule, {@code query.semanticText()} drives the embedding, and
     * {@code query.minSemanticScore()} overrides the default similarity floor. Same guarantees as
     * {@link #findRelevantPassages}.
     */
    List<KnowledgePassage> searchPassages(long companyId, Long agentId, List<Long> extraSourceIds,
                                          KnowledgeQuery query, int limit);

    /**
     * The curated answer to speak for the caller's words, or null. Matched by one of the item's keywords
     * starting a word of {@code question}, or by meaning above the item threshold — never by meaning alone
     * below it, because the answer is read out as written. Same guarantees as {@link #findRelevantPassages};
     * a company with no active items costs no embedding call.
     *
     * @param language the call's language, which picks the answer's translation
     */
    KnowledgeItemMatch findCuratedAnswer(long companyId, Long agentId, String question, String language);
}
