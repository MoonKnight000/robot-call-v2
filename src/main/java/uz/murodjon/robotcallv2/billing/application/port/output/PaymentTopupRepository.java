package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;

import java.util.Optional;

public interface PaymentTopupRepository {

    PaymentTopup save(PaymentTopup topup);

    Optional<PaymentTopup> findByPaymentId(String paymentId);

    /**
     * Whether this company already has a top-up waiting to be paid.
     *
     * <p>Asked before an automatic one is raised: the balance stays low until the
     * customer pays, and without this the scheduler would raise a new payment link on
     * every sweep.
     */
    boolean existsPendingByCompanyId(long companyId);
}
