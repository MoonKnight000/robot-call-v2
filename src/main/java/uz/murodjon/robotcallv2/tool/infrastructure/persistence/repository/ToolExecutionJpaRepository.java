package uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolExecutionEntity;

import java.time.Instant;
import java.util.List;

public interface ToolExecutionJpaRepository extends JpaRepository<ToolExecutionEntity, Long>,
        JpaSpecificationExecutor<ToolExecutionEntity> {

    long countByCompanyIdAndCallAttemptIdAndStatusNot(Long companyId, Long callAttemptId, ToolExecutionStatus status);

    /** Native for {@code percentile_cont}, which JPQL does not have. */
    @Query(value = "SELECT tool_id AS toolId, tool_name AS toolName, COUNT(*) AS total, "
            + "COUNT(*) FILTER (WHERE status = 'OK') AS ok, "
            + "COUNT(*) FILTER (WHERE status = 'HTTP_ERROR') AS httpError, "
            + "COUNT(*) FILTER (WHERE status = 'FAILED') AS failed, "
            + "AVG(latency_ms) AS avgLatencyMs, "
            + "percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95LatencyMs "
            + "FROM tool_execution WHERE company_id = :companyId AND created_at >= :since "
            + "GROUP BY tool_id, tool_name ORDER BY total DESC", nativeQuery = true)
    List<ToolExecutionStatsProjection> summarizeByCompanyIdSince(@Param("companyId") long companyId,
                                                                 @Param("since") Instant since);
}
