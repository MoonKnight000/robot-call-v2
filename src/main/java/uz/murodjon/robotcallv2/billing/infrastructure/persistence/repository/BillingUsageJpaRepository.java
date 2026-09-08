package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingUsageEntity;

import java.util.Optional;

public interface BillingUsageJpaRepository extends JpaRepository<BillingUsageEntity, Long> {

    Optional<BillingUsageEntity> findByCompanyIdAndBillingPeriod(Long companyId, String billingPeriod);
}
