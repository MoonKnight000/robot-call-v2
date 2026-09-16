package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTranscriptEntity;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link CallTranscriptEntity}. */
@Repository
public interface CallTranscriptJpaRepository extends JpaRepository<CallTranscriptEntity, Long> {

    /**
     * The calls whose conversation is past its retention, as the joins and WHERE clause that
     * follow {@code call_attempt a} — the rules are {@code ConversationRetention}'s. Takes
     * {@code :now}, {@code :defaultCutoff} and {@code :zeroPiiCutoff}. The agent is resolved
     * as the summary outbox resolves it: the one the call ran, else its campaign's, else its
     * inbound route's.
     */
    String EXPIRED_CONVERSATION = """
             LEFT JOIN campaign_target ct ON ct.id = a.target_id
             LEFT JOIN campaign cp ON cp.id = ct.campaign_id
             LEFT JOIN inbound_route ir ON ir.id = a.inbound_route_id
             LEFT JOIN ai_agent ag ON ag.id = COALESCE(a.ai_agent_id, cp.ai_agent_id, ir.ai_agent_id)
             WHERE (CASE
                 WHEN ag.zero_pii_retention
                     THEN COALESCE(a.ended_at, COALESCE(a.started_at, a.created_at) + INTERVAL '1 day')
                          < :zeroPiiCutoff
                 WHEN ag.id IS NOT NULL
                     THEN ag.conversation_retention_days > 0
                          AND COALESCE(a.ended_at, a.started_at, a.created_at)
                              < CAST(:now AS timestamptz) - make_interval(days => ag.conversation_retention_days)
                 ELSE COALESCE(a.ended_at, a.started_at, a.created_at) < :defaultCutoff
             END)
            """;

    List<CallTranscriptEntity> findByCall_IdOrderBySeq(long callId);

    /** At most {@code batchSize} lines of conversations past their retention; the summary in call_result stays. */
    @Modifying @Transactional
    @Query(value = "DELETE FROM call_transcript WHERE id IN (SELECT t.id FROM call_transcript t "
            + "JOIN call_attempt a ON a.id = t.call_id " + EXPIRED_CONVERSATION + " LIMIT :batchSize)",
            nativeQuery = true)
    int deleteExpired(@Param("now") Instant now, @Param("defaultCutoff") Instant defaultCutoff,
                      @Param("zeroPiiCutoff") Instant zeroPiiCutoff, @Param("batchSize") int batchSize);
}
