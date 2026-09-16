package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.util.List;
import java.util.Map;

/**
 * Where catalog rows are kept and searched (UNIVERSAL-OPERATOR-PLAN B.4). Every search is
 * narrowed to the company's indexed sources the agent may use — its own and the company-wide
 * ones — and returns candidates, not an answer: {@code CatalogRanker} orders them.
 */
public interface CatalogItemRepository {

    /** Replaces every item of the source in one transaction — a feed is read again whole, not appended to. */
    void replaceBySourceId(long companyId, long sourceId, List<CatalogItem> items);

    void deleteBySourceId(long sourceId);

    /** The vectors the source's items already have, by content hash: what a refresh need not embed again. */
    Map<String, float[]> findEmbeddingsBySourceId(long sourceId);

    /** The items nearest to {@code query} by embedding; {@code score} is the cosine similarity. */
    List<CatalogMatch> findNearest(long companyId, Long agentId, float[] query, int limit);

    /** Items whose words share any of {@code terms}, most shared first; {@code score} is 0. */
    List<CatalogMatch> findByTerms(long companyId, Long agentId, List<String> terms, int limit);

    /** Whether the agent may search any indexed catalog at all. */
    boolean existsByCompanyIdAndAgentId(long companyId, Long agentId);
}
