package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalRunEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentEvalRunJpaRepository extends JpaRepository<AgentEvalRunEntity, Long> {

    Optional<AgentEvalRunEntity> findByIdAndCompanyIdAndAgentId(long id, long companyId, long agentId);

    List<AgentEvalRunEntity> findByCompanyIdAndAgentIdOrderByStartedAtDesc(long companyId, long agentId, Pageable page);

    boolean existsByCompanyIdAndAgentIdAndStatusAndStartedAtAfter(long companyId, long agentId,
                                                                   AgentEvalRunStatus status, Instant since);

    @Modifying
    @Transactional
    @Query("UPDATE AgentEvalRunEntity r SET r.status = :status, r.passed = :passed, r.avgQaScore = :avgQaScore, "
            + "r.finishedAt = :finishedAt, r.error = :error WHERE r.id = :id")
    void finish(@Param("id") long id, @Param("status") AgentEvalRunStatus status, @Param("passed") int passed,
                @Param("avgQaScore") Integer avgQaScore, @Param("finishedAt") Instant finishedAt,
                @Param("error") String error);
}
