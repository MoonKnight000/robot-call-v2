package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentVersionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiAgentVersionJpaRepository extends JpaRepository<AiAgentVersionEntity, Long> {

    Optional<AiAgentVersionEntity> findByCompanyIdAndAgentIdAndStatus(long companyId, long agentId,
                                                                        AgentVersionStatus status);

    List<AiAgentVersionEntity> findByCompanyIdAndAgentIdInAndStatus(long companyId, Collection<Long> agentIds,
                                                                     AgentVersionStatus status);

    Optional<AiAgentVersionEntity> findByCompanyIdAndAgentIdAndVersion(long companyId, long agentId, int version);

    List<AiAgentVersionEntity> findByCompanyIdAndAgentIdOrderByVersionDesc(long companyId, long agentId);

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM AiAgentVersionEntity v WHERE v.agentId = :agentId")
    int findMaxVersion(@Param("agentId") long agentId);

    /**
     * Calls each published version of the agent ran and how many ended in a conversion.
     * Native, because call_attempt belongs to the callrecord feature.
     */
    @Query(value = "SELECT agent_version, count(*), "
            + "count(*) FILTER (WHERE disposition IN ('PROMISE_TO_PAY', 'GOAL_REACHED', 'COMPLETED')) "
            + "FROM call_attempt WHERE company_id = :companyId AND ai_agent_id = :agentId "
            + "AND agent_version IS NOT NULL GROUP BY agent_version", nativeQuery = true)
    List<Object[]> findCallStats(@Param("companyId") long companyId, @Param("agentId") long agentId);
}
