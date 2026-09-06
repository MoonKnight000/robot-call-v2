package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    KnowledgeItem save(KnowledgeItem item);

    Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId);

    Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key);

    List<KnowledgeItem> findAllActiveByCompanyId(long companyId);

    List<KnowledgeItem> findAllByCompanyId(long companyId, KnowledgeItemFilter filter);

    long countByCompanyId(long companyId, KnowledgeItemFilter filter);

    void deleteByIdAndCompanyId(long id, long companyId);
}
