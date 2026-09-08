package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeItemJpaRepository extends JpaRepository<KnowledgeItemEntity, Long> {

    Optional<KnowledgeItemEntity> findByIdAndCompanyId(Long id, Long companyId);

    Optional<KnowledgeItemEntity> findByCompanyIdAndItemKey(Long companyId, String itemKey);

    /**
     * What one agent may answer from: its own items plus the company-wide ones, which is
     * what a null {@code agent_id} means. Called on every caller question, so it is one
     * query rather than two.
     */
    @Query("SELECT k FROM KnowledgeItemEntity k WHERE k.company.id = :companyId AND k.active = true AND "
            + "(k.agent.id IS NULL OR :agentId IS NULL OR k.agent.id = :agentId)")
    List<KnowledgeItemEntity> findAllActiveByCompanyIdAndAgentId(@Param("companyId") Long companyId,
                                                                 @Param("agentId") Long agentId);

    @Query("SELECT k FROM KnowledgeItemEntity k WHERE k.company.id = :companyId AND "
            + "(:agentId IS NULL OR k.agent.id = :agentId) AND "
            + "(:search IS NULL OR "
            + "LOWER(k.itemKey) LIKE :search OR "
            + "LOWER(k.title) LIKE :search OR "
            + "LOWER(k.keywords) LIKE :search OR "
            + "LOWER(k.topic) LIKE :search)")
    Page<KnowledgeItemEntity> searchByCompany(@Param("companyId") Long companyId,
                                              @Param("agentId") Long agentId,
                                              @Param("search") String search,
                                              Pageable pageable);

    @Query("SELECT count(k) FROM KnowledgeItemEntity k WHERE k.company.id = :companyId AND "
            + "(:agentId IS NULL OR k.agent.id = :agentId) AND "
            + "(:search IS NULL OR "
            + "LOWER(k.itemKey) LIKE :search OR "
            + "LOWER(k.title) LIKE :search OR "
            + "LOWER(k.keywords) LIKE :search OR "
            + "LOWER(k.topic) LIKE :search)")
    long countSearchByCompany(@Param("companyId") Long companyId,
                              @Param("agentId") Long agentId,
                              @Param("search") String search);

    void deleteByIdAndCompanyId(Long id, Long companyId);
}
