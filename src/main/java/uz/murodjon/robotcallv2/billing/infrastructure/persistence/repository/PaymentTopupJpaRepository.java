package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;

import java.util.Optional;

public interface PaymentTopupJpaRepository extends JpaRepository<PaymentTopupEntity, Long> {

    Optional<PaymentTopupEntity> findByPaymentId(String paymentId);
}
