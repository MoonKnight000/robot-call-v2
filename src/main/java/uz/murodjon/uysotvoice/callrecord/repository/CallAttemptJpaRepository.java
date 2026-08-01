package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.callrecord.entity.CallAttempt;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link CallAttempt}. */
@Repository
public interface CallAttemptJpaRepository extends JpaRepository<CallAttempt, Long> {

    @Modifying @Transactional
    @Query("UPDATE CallAttempt a SET a.errorMessage = :message WHERE a.id = :id")
    void recordError(@Param("id") long id, @Param("message") String message);

    @Modifying @Transactional
    @Query("UPDATE CallAttempt a SET a.hangupCause = :cause WHERE a.asteriskChannel = :channelId")
    void recordHangupCause(@Param("channelId") String channelId, @Param("cause") String cause);

    @Modifying @Transactional
    @Query("UPDATE CallAttempt a SET a.endedAt = :endedAt, a.durationSec = :durationSec, "
            + "a.disposition = :disposition, a.recordingUrl = :recordingUrl WHERE a.id = :id")
    void finishAttempt(@Param("id") long id, @Param("endedAt") Instant endedAt,
                       @Param("durationSec") int durationSec, @Param("disposition") Disposition disposition,
                       @Param("recordingUrl") String recordingUrl);

    @Modifying @Transactional
    @Query("UPDATE CallAttempt a SET a.finalizeAttempts = a.finalizeAttempts + 1 WHERE a.id = :id")
    void countSummaryAttempt(@Param("id") long id);

    long countByEndedAtGreaterThanEqual(Instant since);

    long countByEndedAtGreaterThanEqualAndDisposition(Instant since, Disposition disposition);

    /** A URL pointing at a deleted object is worse than no URL. */
    @Modifying @Transactional
    @Query("UPDATE CallAttempt a SET a.recordingUrl = NULL "
            + "WHERE a.recordingUrl IS NOT NULL AND a.endedAt IS NOT NULL AND a.endedAt < :cutoff")
    int clearRecordingUrlsEndedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Finished calls that still have no {@code call_result}. Restricted to attempts with
     * transcripts: a call nobody spoke on has nothing to summarize, and would otherwise be
     * retried until its attempt counter ran out. Each row is {@code [callId, clientId]}.
     */
    @Query("SELECT a.id, a.target.clientId FROM CallAttempt a "
            + "WHERE a.endedAt IS NOT NULL AND a.finalizeAttempts < :maxAttempts "
            + "AND NOT EXISTS (SELECT 1 FROM CallResult r WHERE r.call = a) "
            + "AND EXISTS (SELECT 1 FROM CallTranscript c WHERE c.call = a) "
            + "ORDER BY a.id")
    List<Object[]> attemptsAwaitingSummary(@Param("maxAttempts") int maxAttempts, Pageable limit);
}
