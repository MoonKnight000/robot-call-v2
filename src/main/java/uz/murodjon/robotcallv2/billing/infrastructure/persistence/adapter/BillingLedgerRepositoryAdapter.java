package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.application.mapper.BillingLedgerMapper;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.BillingLedgerJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

@Component
public class BillingLedgerRepositoryAdapter implements BillingLedgerRepository {

    private final BillingLedgerJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final BillingLedgerMapper mapper;

    public BillingLedgerRepositoryAdapter(BillingLedgerJpaRepository jpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          BillingLedgerMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean append(LedgerEntry entry) {
        // Checked first for the common case, then again by the unique index: two retries
        // arriving together both pass the check, and only the index can settle that.
        if (jpaRepository.existsByCompanyIdAndIdempotencyKey(entry.companyId(), entry.idempotencyKey())) {
            return false;
        }
        try {
            CompanyEntity company = companyJpaRepository.getReferenceById(entry.companyId());
            jpaRepository.save(mapper.toEntity(entry, company));
            return true;
        } catch (DataIntegrityViolationException alreadyWritten) {
            return false;
        }
    }
}
