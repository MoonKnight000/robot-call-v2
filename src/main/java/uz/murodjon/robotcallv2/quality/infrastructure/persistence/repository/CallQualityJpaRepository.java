package uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallQualityEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallQualityJpaRepository extends JpaRepository<CallQualityEntity, Long>,
        JpaSpecificationExecutor<CallQualityEntity> {

    @Query("SELECT q FROM CallQualityEntity q LEFT JOIN FETCH q.callAttempt a LEFT JOIN FETCH a.aiAgent "
            + "WHERE q.companyId = :companyId AND a.id = :callAttemptId")
    Optional<CallQualityEntity> findByCompanyIdAndCallAttemptId(@Param("companyId") Long companyId,
                                                               @Param("callAttemptId") Long callAttemptId);

    @Modifying
    @Query(value = "UPDATE call_quality SET repeat_call = true WHERE company_id = :companyId AND call_attempt_id IN :callAttemptIds",
            nativeQuery = true)
    int markRepeatCalls(@Param("companyId") Long companyId, @Param("callAttemptIds") Collection<Long> callAttemptIds);

    /**
     * {@code [companyId, aiAgentId, calls]} per agent with rows in the window. A grouped
     * projection rather than the rows themselves: the daily job asks who is worth looking at
     * before it reads anybody's calls.
     */
    @Query("SELECT q.companyId, a.aiAgent.id, COUNT(q) FROM CallQualityEntity q JOIN q.callAttempt a "
            + "WHERE q.createdAt >= :since AND a.aiAgent.id IS NOT NULL GROUP BY q.companyId, a.aiAgent.id")
    List<Object[]> countByAgentSince(@Param("since") Instant since);

    @Query("SELECT q FROM CallQualityEntity q LEFT JOIN FETCH q.callAttempt a LEFT JOIN FETCH a.aiAgent "
            + "WHERE q.companyId = :companyId AND a.aiAgent.id = :aiAgentId AND q.createdAt >= :since")
    List<CallQualityEntity> findByCompanyIdAndAiAgentIdAndCreatedAtGreaterThanEqual(
            @Param("companyId") Long companyId, @Param("aiAgentId") Long aiAgentId, @Param("since") Instant since);
}
