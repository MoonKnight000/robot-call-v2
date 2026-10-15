package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingLedgerEntity;

import java.time.Instant;

public interface BillingLedgerJpaRepository extends JpaRepository<BillingLedgerEntity, Long> {

    boolean existsByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(l.amountUzs), 0) FROM BillingLedgerEntity l
             WHERE l.companyId = :companyId AND l.entryType = :entryType AND l.createdAt >= :since
            """)
    long sumAmount(@Param("companyId") Long companyId, @Param("entryType") LedgerEntryType entryType,
                   @Param("since") Instant since);
}
