package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.PaymentTopupJpaRepository;

import java.util.Optional;

@Component
public class PaymentTopupRepositoryAdapter implements PaymentTopupRepository {

    private final PaymentTopupJpaRepository jpa;

    public PaymentTopupRepositoryAdapter(PaymentTopupJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PaymentTopup save(PaymentTopup topup) {
        PaymentTopupEntity entity = PaymentTopupEntity.fromDomain(topup);
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<PaymentTopup> findByPaymentId(String paymentId) {
        return jpa.findByPaymentId(paymentId).map(PaymentTopupEntity::toDomain);
    }
}
