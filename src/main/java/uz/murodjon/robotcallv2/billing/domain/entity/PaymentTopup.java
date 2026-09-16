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
    /** Whether a new payment may start on it: only while it is still waiting to be paid. */
    public boolean isPayable() {
        return status == TopupStatus.PENDING;
    }

    /** Whether a payment the provider already started on it may still be settled — an expired top-up included. */
    public boolean isSettleable() {
        return TopupStatus.SETTLEABLE.contains(status);
    }

    /** The amount in tiyin (1 UZS = 100 tiyin), the unit Payme counts in. */
    public long amountTiyin() {
        return amountUzs * 100;
    }
}
