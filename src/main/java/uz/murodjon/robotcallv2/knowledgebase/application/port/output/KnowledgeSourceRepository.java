package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceRepository {

    KnowledgeSource save(KnowledgeSource source);

    Optional<KnowledgeSource> findByCompanyIdAndId(long companyId, long id);

    List<KnowledgeSource> findByCompanyId(long companyId);

    List<KnowledgeSource> findByCompanyIdAndAgentId(long companyId, long agentId);

    void deleteByCompanyIdAndId(long companyId, long id);
}
