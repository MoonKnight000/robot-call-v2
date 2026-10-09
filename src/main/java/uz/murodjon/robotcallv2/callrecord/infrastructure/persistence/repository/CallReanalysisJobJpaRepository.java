package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallReanalysisJobEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface CallReanalysisJobJpaRepository extends JpaRepository<CallReanalysisJobEntity, Long>,
        JpaSpecificationExecutor<CallReanalysisJobEntity> {

    Optional<CallReanalysisJobEntity> findByCompanyIdAndId(Long companyId, Long id);

    boolean existsByCompanyIdAndStatusIn(Long companyId, Collection<CallReanalysisStatus> statuses);

    Optional<CallReanalysisJobEntity> findFirstByStatusInOrderById(Collection<CallReanalysisStatus> statuses);

    /** Writes the worker's progress unless the job left {@code active} meanwhile — cancelled from the panel. */
    @Modifying
    @Transactional
    @Query("UPDATE CallReanalysisJobEntity j SET j.status = :status, j.processedCalls = :processedCalls, "
            + "j.updatedCalls = :updatedCalls, j.failedCalls = :failedCalls, j.promptTokens = :promptTokens, "
            + "j.completionTokens = :completionTokens, j.chargedUzs = :chargedUzs, j.lastCallId = :lastCallId, "
            + "j.lastError = :lastError, j.startedAt = :startedAt, j.finishedAt = :finishedAt "
            + "WHERE j.id = :id AND j.status IN :active")
    int updateProgress(@Param("id") long id, @Param("active") Collection<CallReanalysisStatus> active,
                       @Param("status") CallReanalysisStatus status, @Param("processedCalls") int processedCalls,
                       @Param("updatedCalls") int updatedCalls, @Param("failedCalls") int failedCalls,
                       @Param("promptTokens") long promptTokens, @Param("completionTokens") long completionTokens,
                       @Param("chargedUzs") long chargedUzs, @Param("lastCallId") long lastCallId,
                       @Param("lastError") String lastError, @Param("startedAt") Instant startedAt,
                       @Param("finishedAt") Instant finishedAt);

    @Modifying
    @Transactional
    @Query("UPDATE CallReanalysisJobEntity j SET j.status = :cancelled, j.finishedAt = :finishedAt "
            + "WHERE j.companyId = :companyId AND j.id = :id AND j.status IN :active")
    int cancel(@Param("companyId") long companyId, @Param("id") long id,
               @Param("active") Collection<CallReanalysisStatus> active,
               @Param("cancelled") CallReanalysisStatus cancelled, @Param("finishedAt") Instant finishedAt);
}
