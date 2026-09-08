package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeSourceEntity;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, Long> {

    List<KnowledgeSourceEntity> findByCompanyIdOrderByCreatedAtDesc(long companyId);

    List<KnowledgeSourceEntity> findByCompanyIdAndAgentIdOrderByCreatedAtDesc(long companyId, long agentId);

    Optional<KnowledgeSourceEntity> findByCompanyIdAndId(long companyId, long id);

    void deleteByCompanyIdAndId(long companyId, long id);
}
