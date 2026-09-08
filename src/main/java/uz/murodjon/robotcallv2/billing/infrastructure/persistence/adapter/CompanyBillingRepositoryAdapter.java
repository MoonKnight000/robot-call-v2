package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.mapper.CompanyBillingMapper;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CompanyBillingEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.CompanyBillingJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
public class CompanyBillingRepositoryAdapter implements CompanyBillingRepository {

    private final CompanyBillingJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CompanyBillingMapper mapper;

    public CompanyBillingRepositoryAdapter(CompanyBillingJpaRepository jpaRepository,
                                           CompanyJpaRepository companyJpaRepository,
                                           CompanyBillingMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<CompanyBilling> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::toCompanyBilling);
    }

    @Override
    public List<CompanyBilling> findByAutoRechargeEnabled() {
        return jpaRepository.findByAutoRechargeTrue().stream().map(mapper::toCompanyBilling).toList();
    }

    @Override
    public CompanyBilling save(CompanyBilling billing) {
        CompanyEntity company = companyJpaRepository.getReferenceById(billing.companyId());
        CompanyBillingEntity entity = mapper.toEntity(billing, company);
        return mapper.toCompanyBilling(jpaRepository.save(entity));
    }

    @Override
    public Optional<Long> findBalanceForUpdate(long companyId) {
        return jpaRepository.findBalanceForUpdate(companyId);
    }

    @Override
    @Transactional
    public void addBalance(long companyId, long amountUzs) {
        jpaRepository.addBalance(companyId, amountUzs);
    }

    @Override
    @Transactional
    public boolean reserve(long companyId, long amountUzs) {
        return jpaRepository.reserve(companyId, amountUzs) > 0;
    }

    @Override
    @Transactional
    public void release(long companyId, long amountUzs) {
        jpaRepository.release(companyId, amountUzs);
    }

    @Override
    @Transactional
    public void charge(long companyId, long heldUzs, long chargeUzs) {
        jpaRepository.charge(companyId, heldUzs, chargeUzs);
    }
}
