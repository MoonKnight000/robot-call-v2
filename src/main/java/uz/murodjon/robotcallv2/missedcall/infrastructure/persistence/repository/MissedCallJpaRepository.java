package uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.entity.MissedCallEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MissedCallJpaRepository extends JpaRepository<MissedCallEntity, Long>,
        JpaSpecificationExecutor<MissedCallEntity> {

    Optional<MissedCallEntity> findByIdAndCompanyId(long id, long companyId);

    List<MissedCallEntity> findByCompanyIdAndPhoneAndStatusIn(long companyId, String phone,
                                                               Collection<MissedCallStatus> statuses);

    @Modifying
    @Transactional
    @Query("UPDATE MissedCallEntity m SET m.status = :status, m.resolvedAt = :at, m.resolvedByUserId = :userId "
            + "WHERE m.companyId = :companyId AND m.phone = :phone AND m.status IN :open")
    int resolveOpen(@Param("companyId") long companyId, @Param("phone") String phone,
                    @Param("status") MissedCallStatus status, @Param("userId") Long userId,
                    @Param("at") Instant at, @Param("open") Collection<MissedCallStatus> open);

    @Modifying
    @Transactional
    @Query("UPDATE MissedCallEntity m SET m.callbackAttempts = m.callbackAttempts + 1, m.lastCallbackAt = :at "
            + "WHERE m.id = :id")
    void markCallbackAttempt(@Param("id") long id, @Param("at") Instant at);

    /**
     * Takes a missed call for one automatic callback: stamps it as just tried, but only while
     * it is still NEW and due. Of two instances that read the same row from
     * {@link #findAutoCallbackCandidates}, one updates it and rings the caller; the other
     * updates nothing.
     */
    @Modifying
    @Transactional
    @Query("UPDATE MissedCallEntity m SET m.lastCallbackAt = :now "
            + "WHERE m.id = :id AND m.companyId = :companyId "
            + "AND m.status = uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus.NEW "
            + "AND (m.lastCallbackAt IS NULL OR m.lastCallbackAt < :retryBefore)")
    int claimAutoCallback(@Param("id") long id, @Param("companyId") long companyId, @Param("now") Instant now,
                          @Param("retryBefore") Instant retryBefore);

    @Query(value = "SELECT m.* FROM missed_call m JOIN call_queue q ON q.id = m.queue_id "
            + "WHERE q.auto_callback AND q.enabled AND m.status = 'NEW' AND m.callback_attempts < :maxAttempts "
            + "AND (m.last_callback_at IS NULL OR m.last_callback_at < :retryBefore) "
            + "ORDER BY m.last_at LIMIT :limit", nativeQuery = true)
    List<MissedCallEntity> findAutoCallbackCandidates(@Param("retryBefore") Instant retryBefore,
                                                      @Param("maxAttempts") int maxAttempts,
                                                      @Param("limit") int limit);
}
