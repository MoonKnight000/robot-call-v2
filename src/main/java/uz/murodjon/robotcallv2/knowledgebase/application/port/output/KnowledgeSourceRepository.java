package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceRepository {

    KnowledgeSource save(KnowledgeSource source);

    Optional<KnowledgeSource> findByCompanyIdAndId(long companyId, long id);

    List<KnowledgeSource> findByCompanyId(long companyId);

    List<KnowledgeSource> findByCompanyIdAndAgentId(long companyId, long agentId);

    List<KnowledgeSource> findByCompanyIdAndCollectionId(long companyId, long collectionId);

    void deleteByCompanyIdAndId(long companyId, long id);

    /**
     * Every source of this type and status that names a URL and a refresh interval, across
     * all companies — what the feed refresh job looks through.
     */
    List<KnowledgeSource> findRefreshable(KnowledgeSourceType sourceType, KnowledgeSourceStatus status);

    /** Every company's sources in {@code status}; the auto-retry sweep reads the failed ones. */
    List<KnowledgeSource> findByStatus(KnowledgeSourceStatus status);
}
