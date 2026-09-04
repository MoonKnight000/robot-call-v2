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

    private final CompanyBillingJpaRepository jpa;

    public CompanyBillingRepositoryAdapter(CompanyBillingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<CompanyBilling> findByCompanyId(long companyId) {
        return jpa.findByCompanyId(companyId).map(CompanyBillingEntity::toDomain);
    }

    @Override
    public CompanyBilling save(CompanyBilling billing) {
        CompanyBillingEntity entity = CompanyBillingEntity.fromDomain(billing);
        return jpa.save(entity).toDomain();
    }

    @Override
    @Transactional
    public void addBalance(long companyId, long amountUzs) {
        jpa.addBalance(companyId, amountUzs);
    }
}
