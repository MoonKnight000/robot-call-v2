package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CompanyBillingEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.CompanyBillingJpaRepository;

import java.util.Optional;

@Component
public class CompanyBillingRepositoryAdapter implements CompanyBillingRepository {

    private final CompanyBillingJpaRepository jpaRepository;

    public CompanyBillingRepositoryAdapter(CompanyBillingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CompanyBilling> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(CompanyBillingEntity::toDomain);
    }

    @Override
    public CompanyBilling save(CompanyBilling billing) {
        CompanyBillingEntity entity = CompanyBillingEntity.fromDomain(billing);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    @Transactional
    public void addBalance(long companyId, long amountUzs) {
        jpaRepository.addBalance(companyId, amountUzs);
    }
}
