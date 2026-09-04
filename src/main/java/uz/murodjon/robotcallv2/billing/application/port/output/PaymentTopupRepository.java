package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;

import java.util.Optional;

public interface PaymentTopupRepository {

    PaymentTopup save(PaymentTopup topup);

    Optional<PaymentTopup> findByPaymentId(String paymentId);
}
