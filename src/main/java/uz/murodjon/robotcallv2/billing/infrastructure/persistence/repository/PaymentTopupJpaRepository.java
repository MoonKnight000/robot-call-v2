package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface PaymentTopupJpaRepository extends JpaRepository<PaymentTopupEntity, Long> {

    Optional<PaymentTopupEntity> findByPaymentId(String paymentId);

    /** SELECT … FOR UPDATE: the row stays held until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTopupEntity t WHERE t.paymentId = :paymentId")
    Optional<PaymentTopupEntity> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    boolean existsByCompanyIdAndStatusAndCreatedAtAfter(Long companyId, TopupStatus status, Instant createdAt);

    /**
     * Moves one top-up to {@code status}, but only from one of {@code fromStatuses}. The
     * check is in the WHERE clause rather than read-then-write in Java, so that of two
     * callbacks settling the same top-up at once only one can win.
     *
     * @return 1 when it moved, 0 when it was in none of {@code fromStatuses}
     */
    @Modifying
    @Query("""
            UPDATE PaymentTopupEntity t SET t.status = :status, t.paidAt = :paidAt
             WHERE t.id = :id AND t.status IN :fromStatuses
            """)
    int updateStatusAndPaidAt(@Param("id") Long id, @Param("fromStatuses") Collection<TopupStatus> fromStatuses,
                              @Param("status") TopupStatus status, @Param("paidAt") Instant paidAt);

    /** As {@link #updateStatusAndPaidAt}, leaving {@code paidAt} alone. */
    @Modifying
    @Query("UPDATE PaymentTopupEntity t SET t.status = :status WHERE t.id = :id AND t.status IN :fromStatuses")
    int updateStatus(@Param("id") Long id, @Param("fromStatuses") Collection<TopupStatus> fromStatuses,
                     @Param("status") TopupStatus status);

    @Modifying
    @Query("UPDATE PaymentTopupEntity t SET t.status = :status WHERE t.status = :fromStatus AND t.createdAt < :createdBefore")
    int updateStatusCreatedBefore(@Param("fromStatus") TopupStatus fromStatus, @Param("status") TopupStatus status,
                                  @Param("createdBefore") Instant createdBefore);
}
