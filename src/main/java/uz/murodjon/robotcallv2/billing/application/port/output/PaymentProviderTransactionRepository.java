package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The payments Payme and Click started against top-ups, one row per provider transaction. */
public interface PaymentProviderTransactionRepository {

    /**
     * Writes a new transaction, or the new state of one already written. The id,
     * {@code paymentId} and {@code createdAt} are storage's and come back filled in.
     */
    PaymentProviderTransaction save(PaymentProviderTransaction transaction);

    /**
     * The provider's transaction, holding its row until the caller's transaction ends — so a
     * callback the provider repeats waits for the first one to finish and then sees what it
     * did, instead of doing it again.
     */
    Optional<PaymentProviderTransaction> findByProviderTransactionIdForUpdate(PaymentMethod provider,
                                                                              String providerTransactionId);

    /** Same, by our own id (Click's {@code merchant_prepare_id}). */
    Optional<PaymentProviderTransaction> findByIdForUpdate(long id);

    /** Whether the top-up has a transaction in {@code state} — a payment already under way on it, for CREATED. */
    boolean existsByTopupIdAndState(long topupId, ProviderTransactionState state);

    /** The provider's transactions it created between {@code from} and {@code to} inclusive, oldest first. */
    List<PaymentProviderTransaction> findByProviderAndProviderTimeBetween(PaymentMethod provider, Instant from,
                                                                          Instant to);
}
