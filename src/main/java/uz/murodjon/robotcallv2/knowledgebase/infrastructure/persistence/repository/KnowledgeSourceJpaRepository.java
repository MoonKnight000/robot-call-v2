package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeSourceEntity;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, Long> {

    List<KnowledgeSourceEntity> findByCompanyIdOrderByCreatedAtDesc(long companyId);

    List<KnowledgeSourceEntity> findByCompanyIdAndAgentIdOrderByCreatedAtDesc(long companyId, long agentId);

    Optional<KnowledgeSourceEntity> findByCompanyIdAndId(long companyId, long id);

    void deleteByCompanyIdAndId(long companyId, long id);

    @Query("SELECT s FROM KnowledgeSourceEntity s WHERE s.sourceType = :sourceType AND s.status = :status "
            + "AND s.url IS NOT NULL AND s.refreshMinutes IS NOT NULL")
    List<KnowledgeSourceEntity> findRefreshable(@Param("sourceType") KnowledgeSourceType sourceType,
                                                @Param("status") KnowledgeSourceStatus status);
}
