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

    List<KnowledgeItemEntity> findAllByCompanyIdAndActiveTrue(Long companyId);

    @Query("SELECT k FROM KnowledgeItemEntity k WHERE k.companyId = :companyId AND "
            + "(:search IS NULL OR :search = '' OR "
            + "LOWER(k.itemKey) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(k.title) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(k.keywords) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(k.topic) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<KnowledgeItemEntity> searchByCompany(@Param("companyId") Long companyId,
                                              @Param("search") String search,
                                              Pageable pageable);

    void deleteByIdAndCompanyId(Long id, Long companyId);
}
