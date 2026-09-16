package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;
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
}
