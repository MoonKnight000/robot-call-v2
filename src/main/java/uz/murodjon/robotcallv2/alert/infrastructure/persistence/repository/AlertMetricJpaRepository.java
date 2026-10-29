package uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.entity.AlertRuleEntity;

import java.time.Instant;
import java.util.Collection;

/**
 * Alert metrics over call_attempt and the tables hanging off it. Native: they lean on
 * PostgreSQL's {@code FILTER} and on tables this feature has no entity for.
 */
public interface AlertMetricJpaRepository extends Repository<AlertRuleEntity, Long> {

    /** Calls an AI agent ran, of the company, ended in the window; one agent's when {@code aiAgentId} is set. */
    String AI_CALLS_ENDED = " WHERE a.company_id = :companyId AND a.ended_at >= :since AND a.ai_agent_id IS NOT NULL"
            + " AND (CAST(:aiAgentId AS bigint) IS NULL OR a.ai_agent_id = CAST(:aiAgentId AS bigint))";

    @Query(value = "SELECT count(*) AS total, count(*) FILTER (WHERE a.disposition IN (:dispositions)) AS hits "
            + "FROM call_attempt a" + AI_CALLS_ENDED, nativeQuery = true)
    AlertMetricProjection findDispositionCounts(@Param("companyId") long companyId, @Param("aiAgentId") Long aiAgentId,
                                                @Param("since") Instant since,
                                                @Param("dispositions") Collection<String> dispositions);

    @Query(value = "SELECT count(*) AS total, count(*) FILTER (WHERE r.sentiment IN ('NEGATIVE', 'HOSTILE')) AS hits "
            + "FROM call_attempt a JOIN call_result r ON r.call_id = a.id" + AI_CALLS_ENDED, nativeQuery = true)
    AlertMetricProjection findNegativeSentimentCounts(@Param("companyId") long companyId,
                                                      @Param("aiAgentId") Long aiAgentId, @Param("since") Instant since);

    @Query(value = "SELECT count(t.avg_turn_latency_ms) AS total, avg(t.avg_turn_latency_ms) AS average "
            + "FROM call_attempt a JOIN call_technical t ON t.call_id = a.id" + AI_CALLS_ENDED, nativeQuery = true)
    AlertMetricProjection findTurnLatency(@Param("companyId") long companyId, @Param("aiAgentId") Long aiAgentId,
                                          @Param("since") Instant since);

    /** Requests made on calls only — a run of the panel's Test button is not a production error. */
    @Query(value = "SELECT count(*) AS total, count(*) FILTER (WHERE e.status <> 'OK') AS hits "
            + "FROM tool_execution e JOIN call_attempt a ON a.id = e.call_attempt_id "
            + "WHERE e.company_id = :companyId AND e.created_at >= :since "
            + "AND (CAST(:aiAgentId AS bigint) IS NULL OR a.ai_agent_id = CAST(:aiAgentId AS bigint))",
            nativeQuery = true)
    AlertMetricProjection findToolErrorCounts(@Param("companyId") long companyId, @Param("aiAgentId") Long aiAgentId,
                                              @Param("since") Instant since);

    @Query(value = "SELECT count(*) FROM call_attempt a" + AI_CALLS_ENDED, nativeQuery = true)
    long countEndedCalls(@Param("companyId") long companyId, @Param("aiAgentId") Long aiAgentId,
                         @Param("since") Instant since);
}
