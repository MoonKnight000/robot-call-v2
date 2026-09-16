package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;

import java.time.Instant;

/**
 * One payment a provider (Payme, Click) started against a top-up.
 *
 * <p>Keyed by the provider's own transaction id, unique per provider, so a callback the
 * provider repeats finds the row it already wrote instead of starting — or crediting — the
 * same payment twice.
 *
 * @param id                    storage's, null before it is written. It is also what Payme
 *                              knows as {@code transaction} and Click as
 *                              {@code merchant_prepare_id} / {@code merchant_confirm_id}
 * @param provider              PAYME or CLICK
 * @param providerTransactionId Payme's {@code id}, Click's {@code click_trans_id}
 * @param topupId               the top-up it pays
 * @param paymentId             that top-up's public id; read-only, filled in by storage
 * @param amountTiyin           what the provider is paying, in tiyin
 * @param state                 where it stands
 * @param providerTime          when the provider says it created it (Payme's {@code time}),
 *                              what GetStatement filters on; null for Click
 * @param createdAt             storage's to stamp; Payme's 12-hour timeout runs from it
 * @param performedAt           when the balance was credited
 * @param cancelledAt           when it was cancelled
 * @param reason                why it was cancelled: Payme's reason code, or Click's error code
 */
public record PaymentProviderTransaction(
        Long id,
        PaymentMethod provider,
        String providerTransactionId,
        long topupId,
        String paymentId,
        long amountTiyin,
        ProviderTransactionState state,
        Instant providerTime,
        Instant createdAt,
        Instant performedAt,
        Instant cancelledAt,
        Integer reason
) {
    /** A payment the provider has just started on {@code topup}, for its whole amount; the id and createdAt are storage's. */
    public static PaymentProviderTransaction start(PaymentMethod provider, String providerTransactionId,
                                                   PaymentTopup topup, Instant providerTime) {
        return new PaymentProviderTransaction(null, provider, providerTransactionId, topup.id(), topup.paymentId(),
                topup.amountTiyin(), ProviderTransactionState.CREATED, providerTime, null, null, null, null);
    }

    /** The same transaction, performed: the balance was credited at {@code at}. */
    public PaymentProviderTransaction perform(Instant at) {
        return new PaymentProviderTransaction(id, provider, providerTransactionId, topupId, paymentId, amountTiyin,
                ProviderTransactionState.PERFORMED, providerTime, createdAt, at, cancelledAt, reason);
    }

    /** The same transaction, cancelled at {@code at} for {@code cancelReason}. */
    public PaymentProviderTransaction cancel(Instant at, Integer cancelReason) {
        return new PaymentProviderTransaction(id, provider, providerTransactionId, topupId, paymentId, amountTiyin,
                ProviderTransactionState.CANCELLED, providerTime, createdAt, performedAt, at, cancelReason);
    }
}
