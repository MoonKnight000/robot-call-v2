package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.port.output.TrialCreditRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.TrialCreditEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.TrialCreditJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class TrialCreditRepositoryAdapter implements TrialCreditRepository {

    private final TrialCreditJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final TrialCreditEntityMapper mapper;

    public TrialCreditRepositoryAdapter(TrialCreditJpaRepository jpaRepository,
                                        CompanyJpaRepository companyJpaRepository,
                                        TrialCreditEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TrialCredit> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::toTrialCredit);
    }

    @Override
    public boolean create(TrialCredit trial) {
        // Checked first for the common case, then again by the unique index for a race.
        if (jpaRepository.existsByCompanyId(trial.companyId())) {
            return false;
        }
        try {
            CompanyEntity company = companyJpaRepository.getReferenceById(trial.companyId());
            jpaRepository.save(mapper.toTrialCreditEntity(trial, company));
            return true;
        } catch (DataIntegrityViolationException alreadyGranted) {
            return false;
        }
    }

    @Override
    public List<TrialCredit> findUnsettledExpiredBefore(Instant now) {
        return jpaRepository.findBySettledAtIsNullAndExpiresAtBefore(now).stream()
                .map(mapper::toTrialCredit)
                .toList();
    }

    @Override
    @Transactional
    public boolean settle(long companyId, long clawbackUzs, Instant settledAt) {
        return jpaRepository.settle(companyId, clawbackUzs, settledAt) > 0;
    }
}
