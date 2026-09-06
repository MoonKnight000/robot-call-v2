package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    KnowledgeItem save(KnowledgeItem item);

    Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId);

    Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key);

    List<KnowledgeItem> findAllActiveByCompanyId(long companyId);

    Page<KnowledgeItem> findAllByCompanyId(long companyId, String search, Pageable pageable);

    void deleteByIdAndCompanyId(long id, long companyId);
}
