package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    KnowledgeItem save(KnowledgeItem item);

    Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId);

    Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key);

    /**
     * Everything the given agent may answer from — its own items and the company-wide
     * ones. A null {@code agentId} asks for the company's items without narrowing.
     */
    List<KnowledgeItem> findAllActiveByCompanyIdAndAgentId(long companyId, Long agentId);

    /** One page of the filter's items together with the total, from one list query and at most one count. */
    PageableData<KnowledgeItem> findPageByCompanyId(long companyId, KnowledgeItemFilter filter);

    void deleteByIdAndCompanyId(long id, long companyId);

    /** The distinct topics of the company's items, alphabetically. */
    List<String> findTopicsByCompanyId(long companyId);

    /** Stores the item's vector; null clears it, and the backfill fills it in later. */
    void updateEmbedding(long itemId, float[] embedding);

    /**
     * Item id → cosine to {@code query}, for the active items this agent may answer from that have a vector,
     * the {@code limit} closest. Items with no vector are absent.
     */
    Map<Long, Double> findSimilarities(long companyId, Long agentId, float[] query, int limit);

    /** Items with no vector yet, oldest first. */
    List<KnowledgeItem> findWithoutEmbedding(int limit);
}
