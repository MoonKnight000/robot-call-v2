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

    List<CallTranscriptEntity> findByCall_IdOrderBySeq(long callId);

    /** Verbatim speech goes with the audio; the summary in call_result stays. */
    @Modifying @Transactional
    @Query("DELETE FROM CallTranscriptEntity c WHERE c.call.id IN "
            + "(SELECT a.id FROM CallAttemptEntity a WHERE a.endedAt IS NOT NULL AND a.endedAt < :cutoff)")
    int purgeForAttemptsEndedBefore(@Param("cutoff") Instant cutoff);
}
