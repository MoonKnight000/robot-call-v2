package uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentPromiseJpaRepository extends JpaRepository<PaymentPromiseEntity, Long>,
        JpaSpecificationExecutor<PaymentPromiseEntity> {

    Optional<PaymentPromiseEntity> findByCompanyIdAndId(Long companyId, Long id);

    @Query("SELECT p FROM PaymentPromiseEntity p WHERE p.status = uz.murodjon.robotcallv2.promise.domain.enums"
            + ".PaymentPromiseStatus.OPEN AND p.id > :afterId ORDER BY p.id")
    List<PaymentPromiseEntity> findOpenAfterId(@Param("afterId") long afterId, Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO payment_promise (company_id, call_attempt_id, target_id, phone, promised_date, promised_amount)
            VALUES (:companyId, :callAttemptId, CAST(:targetId AS bigint), :phone, CAST(:promisedDate AS date),
                    CAST(:promisedAmount AS numeric))
            ON CONFLICT (call_attempt_id) DO NOTHING
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("companyId") long companyId, @Param("callAttemptId") long callAttemptId,
                                @Param("targetId") Long targetId, @Param("phone") String phone,
                                @Param("promisedDate") LocalDate promisedDate,
                                @Param("promisedAmount") BigDecimal promisedAmount);

    @Modifying
    @Transactional
    @Query(value = "UPDATE payment_promise SET status = 'CANCELLED', resolved_at = CAST(:resolvedAt AS timestamptz) "
            + "WHERE company_id = :companyId AND phone = :phone AND status = 'OPEN' "
            + "AND call_attempt_id <> :exceptCallAttemptId", nativeQuery = true)
    void cancelOpenByCompanyIdAndPhone(@Param("companyId") long companyId, @Param("phone") String phone,
                                       @Param("exceptCallAttemptId") long exceptCallAttemptId,
                                       @Param("resolvedAt") Instant resolvedAt);

    @Modifying
    @Transactional
    @Query(value = "UPDATE payment_promise SET status = :status, resolved_at = CAST(:resolvedAt AS timestamptz) "
            + "WHERE id = :id AND status = 'OPEN'", nativeQuery = true)
    int updateStatusIfOpen(@Param("id") long id, @Param("status") String status,
                           @Param("resolvedAt") Instant resolvedAt);

    @Modifying
    @Transactional
    @Query(value = "UPDATE payment_promise SET reminder_sent_at = CAST(:sentAt AS timestamptz) WHERE id = :id", nativeQuery = true)
    void markReminded(@Param("id") long id, @Param("sentAt") Instant sentAt);

    @Query(value = """
            SELECT count(*)                                              AS total,
                   count(*) FILTER (WHERE status = 'OPEN')               AS open,
                   count(*) FILTER (WHERE status = 'KEPT')               AS kept,
                   count(*) FILTER (WHERE status = 'BROKEN')             AS broken,
                   count(*) FILTER (WHERE status = 'CANCELLED')          AS cancelled,
                   coalesce(sum(promised_amount), 0)                     AS "promisedAmount",
                   coalesce(sum(promised_amount) FILTER (WHERE status = 'KEPT'), 0) AS "keptAmount"
            FROM payment_promise
            WHERE company_id = :companyId AND promised_date BETWEEN :from AND :to
            """, nativeQuery = true)
    PaymentPromiseStatsProjection summarizeByCompanyId(@Param("companyId") long companyId,
                                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Kept and broken promises per number, made since {@code since}: phone, kept, broken. */
    @Query(value = "SELECT phone, count(*) FILTER (WHERE status = 'KEPT'), count(*) FILTER (WHERE status = 'BROKEN') "
            + "FROM payment_promise WHERE company_id = :companyId AND phone IN (:phones) "
            + "AND created_at >= CAST(:since AS timestamptz) AND status IN ('KEPT', 'BROKEN') GROUP BY phone",
            nativeQuery = true)
    List<Object[]> countResolvedByPhone(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                                        @Param("since") Instant since);
}
