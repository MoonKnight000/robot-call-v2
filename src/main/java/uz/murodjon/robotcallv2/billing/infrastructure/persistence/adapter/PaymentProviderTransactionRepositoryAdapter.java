package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.application.port.output.PaymentProviderTransactionRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.PaymentProviderTransactionEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.PaymentProviderTransactionJpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.PaymentTopupJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentProviderTransactionRepositoryAdapter implements PaymentProviderTransactionRepository {

    private final PaymentProviderTransactionJpaRepository jpaRepository;
    private final PaymentTopupJpaRepository paymentTopupJpaRepository;
    private final PaymentProviderTransactionEntityMapper mapper;

    public PaymentProviderTransactionRepositoryAdapter(PaymentProviderTransactionJpaRepository jpaRepository,
                                                       PaymentTopupJpaRepository paymentTopupJpaRepository,
                                                       PaymentProviderTransactionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.paymentTopupJpaRepository = paymentTopupJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public PaymentProviderTransaction save(PaymentProviderTransaction transaction) {
        PaymentTopupEntity topup = paymentTopupJpaRepository.getReferenceById(transaction.topupId());
        return mapper.toPaymentProviderTransaction(
                jpaRepository.save(mapper.toPaymentProviderTransactionEntity(transaction, topup)));
    }

    @Override
    public Optional<PaymentProviderTransaction> findByProviderTransactionIdForUpdate(PaymentMethod provider,
                                                                                     String providerTransactionId) {
        return jpaRepository.findByProviderAndProviderTransactionId(provider, providerTransactionId)
                .map(mapper::toPaymentProviderTransaction);
    }

    @Override
    public Optional<PaymentProviderTransaction> findByIdForUpdate(long id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toPaymentProviderTransaction);
    }

    @Override
    public boolean existsByTopupIdAndState(long topupId, ProviderTransactionState state) {
        return jpaRepository.existsByTopupIdAndState(topupId, state);
    }

    @Override
    public List<PaymentProviderTransaction> findByProviderAndProviderTimeBetween(PaymentMethod provider, Instant from,
                                                                                 Instant to) {
        return jpaRepository.findByProviderAndProviderTimeBetween(provider, from, to).stream()
                .map(mapper::toPaymentProviderTransaction)
                .toList();
    }
}
