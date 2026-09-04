package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingUsageRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingUsageEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.BillingUsageJpaRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class BillingUsageRepositoryAdapter implements BillingUsageRepository {

    private final BillingUsageJpaRepository jpa;

    public BillingUsageRepositoryAdapter(BillingUsageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<BillingUsage> findByCompanyIdAndPeriod(long companyId, String billingPeriod) {
        return jpa.findByCompanyIdAndBillingPeriod(companyId, billingPeriod).map(BillingUsageEntity::toDomain);
    }

    @Override
    public List<BillingUsage> findRecentByCompanyId(long companyId, int limit) {
        List<BillingUsageEntity> list = jpa.findRecentByCompanyId(companyId, PageRequest.of(0, limit));
        // Reverse so that list is chronological (oldest to newest)
        List<BillingUsage> result = new java.util.ArrayList<>(list.stream().map(BillingUsageEntity::toDomain).toList());
        Collections.reverse(result);
        return result;
    }

    @Override
    public BillingUsage save(BillingUsage usage) {
        BillingUsageEntity entity = BillingUsageEntity.fromDomain(usage);
        return jpa.save(entity).toDomain();
    }
}
