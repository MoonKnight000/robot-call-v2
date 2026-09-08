package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.mapper.BillingUsageMapper;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingUsageRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingUsageEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.BillingUsageJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.util.Optional;

@Component
public class BillingUsageRepositoryAdapter implements BillingUsageRepository {

    private final BillingUsageJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final BillingUsageMapper mapper;

    public BillingUsageRepositoryAdapter(BillingUsageJpaRepository jpaRepository,
                                         CompanyJpaRepository companyJpaRepository,
                                         BillingUsageMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<BillingUsage> findByCompanyIdAndPeriod(long companyId, String billingPeriod) {
        return jpaRepository.findByCompanyIdAndBillingPeriod(companyId, billingPeriod).map(mapper::toBillingUsage);
    }

    @Override
    public BillingUsage save(BillingUsage usage) {
        CompanyEntity company = companyJpaRepository.getReferenceById(usage.companyId());
        BillingUsageEntity entity = mapper.toEntity(usage, company);
        return mapper.toBillingUsage(jpaRepository.save(entity));
    }
}
