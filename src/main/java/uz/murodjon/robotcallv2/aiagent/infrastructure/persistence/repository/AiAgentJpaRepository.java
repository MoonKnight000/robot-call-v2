package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiAgentJpaRepository extends JpaRepository<AiAgentEntity, Long>,
        JpaSpecificationExecutor<AiAgentEntity> {

    Optional<AiAgentEntity> findByIdAndCompanyId(long id, long companyId);

    List<AiAgentEntity> findByIdIn(Collection<Long> ids);

    /**
     * Native, because campaign and inbound_route belong to other features and this
     * repository is not the place to hold their JPA entities — it only needs to know
     * whether anything still points here before an agent is deleted.
     */
    @Query(value = "SELECT count(*) FROM campaign WHERE ai_agent_id = :id", nativeQuery = true)
    long countCampaignsUsing(@Param("id") long id);

    @Query(value = "SELECT count(*) FROM inbound_route WHERE ai_agent_id = :id", nativeQuery = true)
    long countInboundRoutesUsing(@Param("id") long id);

    /** Native for the same reason: call_queue is the callqueue feature's table. */
    @Query(value = "SELECT count(*) FROM call_queue WHERE id = :queueId AND company_id = :companyId", nativeQuery = true)
    long countCallQueuesByCompanyIdAndId(@Param("companyId") long companyId, @Param("queueId") long queueId);

    /**
     * One row over the agent's last {@code limit} finished calls: count, charged seconds and
     * UZS, turn latency (median, 90th percentile) and tokens per turn (10th, 90th percentile).
     * Native for the same reason: call_attempt, call_billing and call_technical belong to the
     * callrecord and billing features. Percentiles skip calls with nothing measured.
     */
    @Query(value = "WITH recent AS ("
            + "SELECT id FROM call_attempt WHERE company_id = :companyId AND ai_agent_id = :agentId "
            + "AND ended_at IS NOT NULL AND duration_sec > 0 ORDER BY ended_at DESC LIMIT :limit) "
            + "SELECT count(*), "
            + "COALESCE(sum(cb.duration_sec) FILTER (WHERE cb.status = 'SETTLED'), 0), "
            + "COALESCE(sum(cb.total_uzs) FILTER (WHERE cb.status = 'SETTLED'), 0), "
            + "percentile_cont(0.5) WITHIN GROUP (ORDER BY ct.avg_turn_latency_ms), "
            + "percentile_cont(0.9) WITHIN GROUP (ORDER BY ct.avg_turn_latency_ms), "
            + "percentile_cont(0.1) WITHIN GROUP (ORDER BY (ct.prompt_tokens + ct.completion_tokens)::float "
            + "/ NULLIF(ct.turn_count, 0)), "
            + "percentile_cont(0.9) WITHIN GROUP (ORDER BY (ct.prompt_tokens + ct.completion_tokens)::float "
            + "/ NULLIF(ct.turn_count, 0)) "
            + "FROM recent r "
            + "LEFT JOIN call_billing cb ON cb.call_attempt_id = r.id "
            + "LEFT JOIN call_technical ct ON ct.call_id = r.id", nativeQuery = true)
    List<Object[]> findRecentCallStats(@Param("companyId") long companyId, @Param("agentId") long agentId,
                                       @Param("limit") int limit);
}
