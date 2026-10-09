package uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallScorecardEntity;

import java.util.Optional;

@Repository
public interface CallScorecardJpaRepository extends JpaRepository<CallScorecardEntity, Long>,
        JpaSpecificationExecutor<CallScorecardEntity> {

    Optional<CallScorecardEntity> findByCallIdAndCompanyId(long callId, long companyId);

    @Modifying
    @Query(value = """
            UPDATE call_scorecard_answer SET evidence = NULL
            WHERE id IN (SELECT a.id FROM call_scorecard_answer a
                         WHERE a.evidence IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM call_transcript t WHERE t.call_id = a.call_id)
                         LIMIT :batchSize)
            """, nativeQuery = true)
    int clearEvidenceWithoutTranscript(@Param("batchSize") int batchSize);
}
