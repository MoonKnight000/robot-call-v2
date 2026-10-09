package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link CallAttemptEntity}. */
@Repository
public interface CallAttemptJpaRepository extends JpaRepository<CallAttemptEntity, Long> {

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.errorMessage = :message WHERE a.id = :id")
    void recordError(@Param("id") long id, @Param("message") String message);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.language = :language WHERE a.id = :id")
    void updateLanguage(@Param("id") long id, @Param("language") String language);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.hangupCause = :cause WHERE a.asteriskChannel = :channelId")
    void recordHangupCause(@Param("channelId") String channelId, @Param("cause") String cause);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.endedAt = :endedAt, a.durationSec = :durationSec, "
            + "a.disposition = :disposition, a.recordingFile.id = :recordingFileId WHERE a.id = :id")
    void finishAttempt(@Param("id") long id, @Param("endedAt") Instant endedAt,
                       @Param("durationSec") int durationSec, @Param("disposition") Disposition disposition,
                       @Param("recordingFileId") Long recordingFileId);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.disposition = :disposition WHERE a.id = :id")
    void updateDisposition(@Param("id") long id, @Param("disposition") Disposition disposition);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.answeredAt = :answeredAt WHERE a.id = :id")
    void markAnswered(@Param("id") long id, @Param("answeredAt") Instant answeredAt);

    /**
     * Close out a call nobody ever picked up. The {@code answeredAt IS NULL} guard is
     * what makes this safe to fire at every destroyed channel: an answered call's own
     * teardown owns its outcome and must not be overwritten.
     *
     * <p>{@code durationSec} is deliberately left null — a call that never connected has
     * no talk time. Whether it was answered is {@code answeredAt}, which reports read.
     */
    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.endedAt = :endedAt, a.disposition = :disposition "
            + "WHERE a.asteriskChannel = :channelId AND a.answeredAt IS NULL AND a.endedAt IS NULL")
    void finishUnanswered(@Param("channelId") String channelId, @Param("endedAt") Instant endedAt,
                          @Param("disposition") Disposition disposition);

    /**
     * The attempt opened for this channel when the call was dialled. Ordered newest
     * first: Asterisk channel ids are unique in practice, but a stale row must never
     * win over the call actually in progress.
     */
    @Query("SELECT a.id FROM CallAttemptEntity a WHERE a.asteriskChannel = :channelId ORDER BY a.id DESC")
    List<Long> findIdsByChannel(@Param("channelId") String channelId, Pageable limit);

    /** §15 "Bugun" mini-statistika — which panel user answered a transferred call. */
    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.operatorUser.id = :userId WHERE a.id = :id")
    void assignOperator(@Param("id") long id, @Param("userId") long userId);

    @Modifying @Transactional
    @Query(value = "UPDATE call_attempt SET queue_id = :queueId, enqueued_at = COALESCE(enqueued_at, :at) WHERE id = :id",
            nativeQuery = true)
    void markEnqueued(@Param("id") long id, @Param("queueId") long queueId, @Param("at") Instant at);

    @Modifying @Transactional
    @Query(value = "UPDATE call_attempt SET operator_user_id = :userId, operator_answered_at = :at, wait_seconds = :waitSeconds "
            + "WHERE id = :id", nativeQuery = true)
    void markOperatorAnswered(@Param("id") long id, @Param("userId") long userId, @Param("at") Instant at,
                              @Param("waitSeconds") int waitSeconds);

    @Modifying @Transactional
    @Query(value = "UPDATE call_attempt SET abandoned = true, wait_seconds = :waitSeconds WHERE id = :id", nativeQuery = true)
    void markAbandoned(@Param("id") long id, @Param("waitSeconds") int waitSeconds);

    @Modifying @Transactional
    @Query(value = "UPDATE call_attempt SET ai_overflow = true, wait_seconds = :waitSeconds WHERE id = :id", nativeQuery = true)
    void markAiOverflow(@Param("id") long id, @Param("waitSeconds") int waitSeconds);

    @Modifying @Transactional
    @Query(value = "UPDATE call_attempt SET survey_score = :score WHERE id = :id", nativeQuery = true)
    void recordSurveyScore(@Param("id") long id, @Param("score") int score);

    @Query(value = "SELECT operator_user_id FROM call_attempt WHERE company_id = :companyId AND phone = :phone "
            + "AND operator_user_id IS NOT NULL AND started_at >= :since ORDER BY started_at DESC LIMIT 1", nativeQuery = true)
    List<Long> findLastOperatorUserId(@Param("companyId") long companyId, @Param("phone") String phone,
                                      @Param("since") Instant since);

    /** Which company an in-flight call belongs to (§11 ai-model settings) — {@code DialogEngine} resolves this once per call. */
    @Query("SELECT a.company.id FROM CallAttemptEntity a WHERE a.id = :id")
    Long findCompanyIdById(@Param("id") long id);

    Optional<CallAttemptEntity> findByIdAndCompanyId(long id, long companyId);

    @Query("SELECT a.id FROM CallAttemptEntity a WHERE a.company.id = :companyId AND a.target.id = :targetId "
            + "AND a.startedAt >= :since ORDER BY a.startedAt DESC, a.id DESC")
    List<Long> findIdsByTargetIdSince(@Param("companyId") long companyId, @Param("targetId") long targetId,
                                      @Param("since") Instant since, Pageable limit);

    /** Which target this call attempt belongs to. */
    @Query("SELECT a.target.id FROM CallAttemptEntity a WHERE a.id = :id")
    Long findTargetIdById(@Param("id") long id);

    /** The number this attempt talked to: dialed for outbound, the caller's for inbound. */
    @Query("SELECT a.phone FROM CallAttemptEntity a WHERE a.id = :id")
    String findPhoneById(@Param("id") long id);

    /**
     * Whether the call came in rather than went out. An inbound route is only ever set on
     * a call this platform answered, so its presence is the direction — there is no
     * direction column, and the CRM's call history wants one.
     */
    @Query("SELECT CASE WHEN a.inboundRoute IS NULL THEN false ELSE true END "
            + "FROM CallAttemptEntity a WHERE a.id = :id")
    Boolean findInboundById(@Param("id") long id);

    @Query("SELECT a.inboundRoute.id FROM CallAttemptEntity a WHERE a.id = :id")
    Long findInboundRouteIdById(@Param("id") long id);

    /**
     * Answered calls this company made to that number in a time range, newest first.
     *
     * <p>A projection rather than whole entities: the caller compares answer times to
     * decide which call earned a conversion, and the lazy relations on a full attempt
     * would each be a query it never reads.
     */
    @Query("SELECT a.id, t.campaign.id, a.variant.id, a.answeredAt FROM CallAttemptEntity a LEFT JOIN a.target t "
            + "WHERE a.company.id = :companyId AND a.phone = :phone "
            + "AND a.answeredAt IS NOT NULL AND a.answeredAt BETWEEN :from AND :to "
            + "ORDER BY a.answeredAt DESC")
    List<Object[]> findAnsweredByPhone(@Param("companyId") long companyId, @Param("phone") String phone,
                                       @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Start times of the outbound attempts that actually went to the trunk (a channel was
     * created), newest first. Inbound calls and attempts abandoned before dialling are not
     * "calling the number" and do not count against a contact cap.
     */
    @Query(value = "SELECT a.started_at FROM call_attempt a "
            + "WHERE a.company_id = :companyId AND a.phone = :phone AND a.inbound_route_id IS NULL "
            + "AND a.asterisk_channel IS NOT NULL AND a.started_at >= :since "
            + "ORDER BY a.started_at DESC LIMIT :limit", nativeQuery = true)
    List<Object> findRecentPlacedStartTimes(@Param("companyId") long companyId, @Param("phone") String phone,
                                            @Param("since") Instant since, @Param("limit") int limit);

    /**
     * When someone last actually talked with this number, in either direction. A machine,
     * a wrong person, or a callback the person asked for is not the conversation a quiet
     * period is about.
     */
    @Query(value = "SELECT max(a.answered_at) FROM call_attempt a "
            + "WHERE a.company_id = :companyId AND a.phone = :phone AND a.answered_at IS NOT NULL "
            + "AND (a.disposition IS NULL OR a.disposition NOT IN "
            + "('VOICEMAIL', 'NO_ANSWER', 'CARRIER_REJECTED', 'FAILED', 'WRONG_NUMBER', 'CALLBACK_REQUESTED'))",
            nativeQuery = true)
    Object findLastConversationAt(@Param("companyId") long companyId, @Param("phone") String phone);

    /**
     * Placed outbound calls to the numbers, one row per (phone, local answer hour): phone, the
     * hour a reached call was answered in {@code zone} (null for the calls nobody talked on),
     * placed, reached and refused counts. "Reached" leaves out what
     * {@link #findLastConversationAt} leaves out, a callback request aside — that is a person.
     */
    @Query(value = "WITH a AS ("
            + "  SELECT phone, disposition, answered_at, answered_at IS NOT NULL AND (disposition IS NULL "
            + "         OR disposition NOT IN ('VOICEMAIL', 'NO_ANSWER', 'CARRIER_REJECTED', 'FAILED', 'WRONG_NUMBER'))"
            + "         AS reached"
            + "  FROM call_attempt"
            + "  WHERE company_id = :companyId AND phone IN (:phones) AND inbound_route_id IS NULL"
            + "    AND asterisk_channel IS NOT NULL AND started_at >= :since"
            + ") SELECT phone,"
            + "         CASE WHEN reached THEN CAST(EXTRACT(HOUR FROM answered_at AT TIME ZONE :zone) AS int) END,"
            + "         count(*), count(*) FILTER (WHERE reached), count(*) FILTER (WHERE disposition = 'REFUSED')"
            + "  FROM a GROUP BY 1, 2", nativeQuery = true)
    List<Object[]> countPlacedByPhoneAndHour(@Param("companyId") long companyId,
                                             @Param("phones") Collection<String> phones,
                                             @Param("since") Instant since, @Param("zone") String zone);

    @Query(value = "SELECT max(r.promised_date) FROM call_result r JOIN call_attempt a ON a.id = r.call_id "
            + "WHERE a.company_id = :companyId AND a.phone = :phone", nativeQuery = true)
    Object findLatestPromisedDate(@Param("companyId") long companyId, @Param("phone") String phone);

    long countByEndedAtGreaterThanEqual(Instant since);

    long countByEndedAtGreaterThanEqualAndDisposition(Instant since, Disposition disposition);

    /**
     * Claims finished calls that still have no {@code call_result}, for the summary retry.
     * Restricted to attempts with transcripts: a call nobody spoke on has nothing to summarize,
     * and would otherwise be retried until its attempt counter ran out. A claimed call counts
     * one attempt and is skipped by every sweep until {@code leaseUntil}; {@code SKIP LOCKED}
     * lets two instances claiming at once take different calls. Each row is
     * {@code [callId, clientId, scenarioId, disposition, agentId]} (ROADMAP A.3 — the summary
     * retry needs the same scenario the live call ran, for its {@code outcomeSchema}).
     */
    // The agent the call actually ran (a.ai_agent_id) wins over the target's campaign agent,
    // and the target is a LEFT JOIN: inbound, manual and widget calls have no target, and
    // their clientId comes back null (read as 0). FOR UPDATE OF a locks the attempt row only:
    // the joined rows are on the nullable side of an outer join.
    @Transactional
    @Query(value = "WITH due AS ("
            + "  SELECT a.id, t.client_id, COALESCE(ag.scenario_id, cag.scenario_id) AS scenario_id,"
            + "         a.disposition, COALESCE(ag.id, cag.id) AS agent_id"
            + "  FROM call_attempt a"
            + "  LEFT JOIN campaign_target t ON t.id = a.target_id"
            + "  LEFT JOIN campaign cp ON cp.id = t.campaign_id"
            + "  LEFT JOIN ai_agent cag ON cag.id = cp.ai_agent_id"
            + "  LEFT JOIN ai_agent ag ON ag.id = a.ai_agent_id"
            + "  WHERE a.ended_at >= :endedAfter AND a.finalize_attempts < :maxAttempts"
            + "    AND (a.summary_lease_until IS NULL OR a.summary_lease_until <= :now)"
            + "    AND COALESCE(ag.scenario_id, cag.scenario_id) IS NOT NULL"
            + "    AND COALESCE(ag.id, cag.id) IS NOT NULL"
            + "    AND NOT EXISTS (SELECT 1 FROM call_result r WHERE r.call_id = a.id)"
            + "    AND EXISTS (SELECT 1 FROM call_transcript c WHERE c.call_id = a.id)"
            + "  ORDER BY a.id LIMIT :limit"
            + "  FOR UPDATE OF a SKIP LOCKED"
            + ") "
            + "UPDATE call_attempt a SET finalize_attempts = a.finalize_attempts + 1, summary_lease_until = :leaseUntil "
            + "FROM due WHERE a.id = due.id "
            + "RETURNING due.id, due.client_id, due.scenario_id, due.disposition, due.agent_id", nativeQuery = true)
    List<Object[]> claimAttemptsAwaitingSummary(@Param("maxAttempts") int maxAttempts,
                                                @Param("endedAfter") Instant endedAfter, @Param("now") Instant now,
                                                @Param("leaseUntil") Instant leaseUntil, @Param("limit") int limit);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.aiAgentId = :agentId, a.agentVersion = :version WHERE a.id = :id")
    void recordAgent(@Param("id") long id, @Param("agentId") Long agentId, @Param("version") Integer version);

    /** Up to {@code batchSize} recordings of calls whose conversation is past its retention. */
    @Query(value = "SELECT a.recording_file_id FROM call_attempt a " + CallTranscriptJpaRepository.EXPIRED_CONVERSATION
            + " AND a.recording_file_id IS NOT NULL LIMIT :batchSize", nativeQuery = true)
    List<Long> findExpiredRecordingFileIds(@Param("now") Instant now, @Param("defaultCutoff") Instant defaultCutoff,
                                           @Param("zeroPiiCutoff") Instant zeroPiiCutoff,
                                           @Param("batchSize") int batchSize);
}
