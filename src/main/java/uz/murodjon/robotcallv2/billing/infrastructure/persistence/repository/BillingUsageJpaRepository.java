package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingUsageEntity;

import java.util.List;
import java.util.Optional;

public interface BillingUsageJpaRepository extends JpaRepository<BillingUsageEntity, Long> {

    Optional<BillingUsageEntity> findByCompanyIdAndBillingPeriod(Long companyId, String billingPeriod);

    @Query("SELECT u FROM BillingUsageEntity u WHERE u.companyId = :companyId ORDER BY u.billingPeriod DESC")
    List<BillingUsageEntity> findRecentByCompanyId(@Param("companyId") Long companyId, Pageable pageable);
}
