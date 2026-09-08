package uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolEntity;

import java.util.List;
import java.util.Optional;

public interface ToolJpaRepository extends JpaRepository<ToolEntity, Long> {

    Optional<ToolEntity> findByCompanyIdAndId(long companyId, long id);

    List<ToolEntity> findAllByCompanyId(long companyId);

    Page<ToolEntity> findAllByCompanyId(long companyId, Pageable pageable);

    @Query("SELECT t FROM ToolEntity t WHERE t.company.id = :companyId " +
            "AND (:search IS NULL OR LOWER(t.name) LIKE :search OR LOWER(t.description) LIKE :search)")
    Page<ToolEntity> searchTools(@Param("companyId") long companyId,
                                 @Param("search") String search,
                                 Pageable pageable);

    @Query("SELECT t FROM ToolEntity t JOIN AiAgentToolEntity at ON t.id = at.id.toolId " +
            "WHERE t.company.id = :companyId AND at.id.aiAgentId = :agentId")
    List<ToolEntity> findToolsByAgentId(@Param("companyId") long companyId, @Param("agentId") long agentId);
}
