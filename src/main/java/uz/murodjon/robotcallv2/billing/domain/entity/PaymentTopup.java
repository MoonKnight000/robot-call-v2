package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;

import java.time.Instant;

public record PaymentTopup(
        Long id,
        String paymentId,
        long companyId,
        Long userId,
        long amountUzs,
        PaymentMethod paymentMethod,
        TopupStatus status,
        String checkoutUrl,
        Instant createdAt,
        Instant paidAt
) {
}
