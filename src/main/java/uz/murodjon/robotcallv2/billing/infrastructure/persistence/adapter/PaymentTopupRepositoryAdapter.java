package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.mapper.PaymentTopupMapper;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.PaymentTopupJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.util.Optional;

@Component
public class PaymentTopupRepositoryAdapter implements PaymentTopupRepository {

    private final PaymentTopupJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PaymentTopupMapper mapper;

    public PaymentTopupRepositoryAdapter(PaymentTopupJpaRepository jpaRepository,
                                         CompanyJpaRepository companyJpaRepository,
                                         UserJpaRepository userJpaRepository,
                                         PaymentTopupMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PaymentTopup save(PaymentTopup topup) {
        CompanyEntity company = companyJpaRepository.getReferenceById(topup.companyId());
        PaymentTopupEntity entity = mapper.toEntity(topup, company, userReference(topup.userId()));
        return mapper.toPaymentTopup(jpaRepository.save(entity));
    }

    @Override
    public Optional<PaymentTopup> findByPaymentId(String paymentId) {
        return jpaRepository.findByPaymentId(paymentId).map(mapper::toPaymentTopup);
    }

    @Override
    public boolean existsPendingByCompanyId(long companyId) {
        return jpaRepository.existsByCompanyIdAndStatus(companyId, TopupStatus.PENDING);
    }

    /** Null for a top-up started by the company rather than a named user. */
    private UserEntity userReference(Long id) {
        return id != null ? userJpaRepository.getReferenceById(id) : null;
    }
}
