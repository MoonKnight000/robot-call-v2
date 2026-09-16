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

    Optional<CallQualityEntity> findByCompanyIdAndCallAttemptId(Long companyId, Long callAttemptId);

    @Modifying
    @Query("UPDATE CallQualityEntity q SET q.repeatCall = true "
            + "WHERE q.companyId = :companyId AND q.callAttemptId IN :callAttemptIds")
    int markRepeatCalls(@Param("companyId") Long companyId, @Param("callAttemptIds") Collection<Long> callAttemptIds);

    /**
     * {@code [companyId, aiAgentId, calls]} per agent with rows in the window. A grouped
     * projection rather than the rows themselves: the daily job asks who is worth looking at
     * before it reads anybody's calls.
     */
    @Query("SELECT q.companyId, q.aiAgentId, COUNT(q) FROM CallQualityEntity q "
            + "WHERE q.createdAt >= :since AND q.aiAgentId IS NOT NULL GROUP BY q.companyId, q.aiAgentId")
    List<Object[]> countByAgentSince(@Param("since") Instant since);

    List<CallQualityEntity> findByCompanyIdAndAiAgentIdAndCreatedAtGreaterThanEqual(
            Long companyId, Long aiAgentId, Instant since);
}
