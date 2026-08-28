package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.callrecord.entity.CallAttemptEntity;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link CallAttemptEntity}. */
@Repository
public interface CallAttemptJpaRepository extends JpaRepository<CallAttemptEntity, Long> {

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.errorMessage = :message WHERE a.id = :id")
    void recordError(@Param("id") long id, @Param("message") String message);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.hangupCause = :cause WHERE a.asteriskChannel = :channelId")
    void recordHangupCause(@Param("channelId") String channelId, @Param("cause") String cause);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.endedAt = :endedAt, a.durationSec = :durationSec, "
            + "a.disposition = :disposition, a.recordingFileId = :recordingFileId WHERE a.id = :id")
    void finishAttempt(@Param("id") long id, @Param("endedAt") Instant endedAt,
                       @Param("durationSec") int durationSec, @Param("disposition") Disposition disposition,
                       @Param("recordingFileId") Long recordingFileId);

    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.finalizeAttempts = a.finalizeAttempts + 1 WHERE a.id = :id")
    void countSummaryAttempt(@Param("id") long id);

    /** §15 "Bugun" mini-statistika — which panel user answered a transferred call. */
    @Modifying @Transactional
    @Query("UPDATE CallAttemptEntity a SET a.operatorUserId = :userId WHERE a.id = :id")
    void assignOperator(@Param("id") long id, @Param("userId") long userId);

    /** Which company an in-flight call belongs to (§11 ai-model settings) — {@code DialogEngine} resolves this once per call. */
    @Query("SELECT a.companyId FROM CallAttemptEntity a WHERE a.id = :id")
    Long findCompanyIdById(@Param("id") long id);

    /** Which target this call attempt belongs to. */
    @Query("SELECT a.target.id FROM CallAttemptEntity a WHERE a.id = :id")
    Long findTargetIdById(@Param("id") long id);

    long countByEndedAtGreaterThanEqual(Instant since);

    long countByEndedAtGreaterThanEqualAndDisposition(Instant since, Disposition disposition);

    /**
     * Finished calls that still have no {@code call_result}. Restricted to attempts with
     * transcripts: a call nobody spoke on has nothing to summarize, and would otherwise be
     * retried until its attempt counter ran out. Each row is
     * {@code [callId, clientId, scenarioId]} (ROADMAP A.3 — the summary retry needs the
     * same scenario the live call ran, for its {@code outcomeSchema}).
     */
    @Query("SELECT a.id, a.target.clientId, a.target.campaign.scenarioId FROM CallAttemptEntity a "
            + "WHERE a.endedAt IS NOT NULL AND a.finalizeAttempts < :maxAttempts "
            + "AND NOT EXISTS (SELECT 1 FROM CallResultEntity r WHERE r.call = a) "
            + "AND EXISTS (SELECT 1 FROM CallTranscriptEntity c WHERE c.call = a) "
            + "ORDER BY a.id")
    List<Object[]> attemptsAwaitingSummary(@Param("maxAttempts") int maxAttempts, Pageable limit);
}
