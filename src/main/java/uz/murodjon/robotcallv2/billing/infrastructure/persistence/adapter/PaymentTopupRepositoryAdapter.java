package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.PaymentTopupEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.PaymentTopupJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Component
public class PaymentTopupRepositoryAdapter implements PaymentTopupRepository {

    private final PaymentTopupJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PaymentTopupEntityMapper mapper;

    public PaymentTopupRepositoryAdapter(PaymentTopupJpaRepository jpaRepository,
                                         CompanyJpaRepository companyJpaRepository,
                                         UserJpaRepository userJpaRepository,
                                         PaymentTopupEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PaymentTopup save(PaymentTopup topup) {
        CompanyEntity company = companyJpaRepository.getReferenceById(topup.companyId());
        PaymentTopupEntity entity = mapper.toPaymentTopupEntity(topup, company, userReference(topup.userId()));
        return mapper.toPaymentTopup(jpaRepository.save(entity));
    }

    @Override
    public Optional<PaymentTopup> findByPaymentId(String paymentId) {
        return jpaRepository.findByPaymentId(paymentId).map(mapper::toPaymentTopup);
    }

    @Override
    public Optional<PaymentTopup> findByPaymentIdForUpdate(String paymentId) {
        return jpaRepository.findByPaymentIdForUpdate(paymentId).map(mapper::toPaymentTopup);
    }

    @Override
    public boolean existsPendingByCompanyIdCreatedAfter(long companyId, Instant createdAfter) {
        return jpaRepository.existsByCompanyIdAndStatusAndCreatedAtAfter(companyId, TopupStatus.PENDING, createdAfter);
    }

    @Override
    @Transactional
    public boolean completeById(long topupId, Instant paidAt) {
        return jpaRepository.updateStatusAndPaidAt(topupId, TopupStatus.SETTLEABLE, TopupStatus.COMPLETED, paidAt) > 0;
    }

    @Override
    @Transactional
    public boolean cancelById(long topupId) {
        return jpaRepository.updateStatus(topupId, TopupStatus.SETTLEABLE, TopupStatus.CANCELLED) > 0;
    }

    @Override
    @Transactional
    public int expirePendingCreatedBefore(Instant createdBefore) {
        return jpaRepository.updateStatusCreatedBefore(TopupStatus.PENDING, TopupStatus.EXPIRED, createdBefore);
    }

    /** Null for a top-up started by the company rather than a named user. */
    private UserEntity userReference(Long id) {
        return id != null ? userJpaRepository.getReferenceById(id) : null;
    }
}
