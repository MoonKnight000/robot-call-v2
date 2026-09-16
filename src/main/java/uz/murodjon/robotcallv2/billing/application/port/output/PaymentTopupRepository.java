package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;

import java.time.Instant;
import java.util.Optional;

public interface PaymentTopupRepository {

    PaymentTopup save(PaymentTopup topup);

    Optional<PaymentTopup> findByPaymentId(String paymentId);

    /**
     * Same, holding the row until the caller's transaction ends — so two payments starting
     * on one top-up at the same moment are decided one after the other.
     */
    Optional<PaymentTopup> findByPaymentIdForUpdate(String paymentId);

    /**
     * Whether this company has a top-up waiting to be paid that was raised after
     * {@code createdAfter}.
     *
     * <p>Asked before an automatic one is raised: the balance stays low until the customer
     * pays, and without this the scheduler would raise a new payment link on every sweep.
     * The cut-off is what keeps a link nobody paid from blocking auto-recharge for good,
     * even before the expiry sweep has marked it EXPIRED.
     */
    boolean existsPendingByCompanyIdCreatedAfter(long companyId, Instant createdAfter);

    /**
     * Moves a top-up that can still be settled ({@link PaymentTopup#isSettleable()}) to
     * COMPLETED. Storage decides, not the caller, so of two callbacks settling the same
     * top-up at once only one can win.
     *
     * @return true when this call moved it — the caller that gets true is the one that
     *         credits the balance
     */
    boolean completeById(long topupId, Instant paidAt);

    /**
     * Moves a top-up that can still be settled to CANCELLED.
     *
     * @return false when it was already paid or closed, and nothing changed
     */
    boolean cancelById(long topupId);

    /**
     * Marks every PENDING top-up raised before {@code createdBefore} EXPIRED.
     *
     * @return how many were expired
     */
    int expirePendingCreatedBefore(Instant createdBefore);
}
