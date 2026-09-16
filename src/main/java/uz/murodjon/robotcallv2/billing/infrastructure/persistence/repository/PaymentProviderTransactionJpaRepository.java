package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentProviderTransactionEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentProviderTransactionJpaRepository extends JpaRepository<PaymentProviderTransactionEntity, Long> {

    /** SELECT … FOR UPDATE: the row stays held until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentProviderTransactionEntity> findByProviderAndProviderTransactionId(PaymentMethod provider,
                                                                                      String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentProviderTransactionEntity t WHERE t.id = :id")
    Optional<PaymentProviderTransactionEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTopupIdAndState(Long topupId, ProviderTransactionState state);

    /** The top-up is fetched with each row: every one of them is answered with its payment id. */
    @Query("""
            SELECT t FROM PaymentProviderTransactionEntity t JOIN FETCH t.topup
             WHERE t.provider = :provider AND t.providerTime BETWEEN :from AND :to
             ORDER BY t.providerTime
            """)
    List<PaymentProviderTransactionEntity> findByProviderAndProviderTimeBetween(@Param("provider") PaymentMethod provider,
                                                                                @Param("from") Instant from,
                                                                                @Param("to") Instant to);
}
