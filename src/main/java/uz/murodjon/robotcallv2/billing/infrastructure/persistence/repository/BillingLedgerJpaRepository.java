package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingLedgerEntity;

public interface BillingLedgerJpaRepository extends JpaRepository<BillingLedgerEntity, Long> {

    boolean existsByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);
}
