package uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentProviderTransactionEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;

@Component
public class PaymentProviderTransactionEntityMapper {

    public PaymentProviderTransaction toPaymentProviderTransaction(PaymentProviderTransactionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentProviderTransaction(
                entity.getId(),
                entity.getProvider(),
                entity.getProviderTransactionId(),
                entity.getTopupId(),
                entity.getTopup().getPaymentId(),
                entity.getAmountTiyin(),
                entity.getState(),
                entity.getProviderTime(),
                entity.getCreatedAt(),
                entity.getPerformedAt(),
                entity.getCancelledAt(),
                entity.getReason());
    }

    /** {@code paymentId} is not written: it is the top-up's, reached through {@code topup}. */
    public PaymentProviderTransactionEntity toPaymentProviderTransactionEntity(PaymentProviderTransaction transaction,
                                                                              PaymentTopupEntity topup) {
        if (transaction == null) {
            return null;
        }
        PaymentProviderTransactionEntity entity = new PaymentProviderTransactionEntity();
        entity.setId(transaction.id());
        entity.setProvider(transaction.provider());
        entity.setProviderTransactionId(transaction.providerTransactionId());
        entity.setTopup(topup);
        entity.setAmountTiyin(transaction.amountTiyin());
        entity.setState(transaction.state());
        entity.setProviderTime(transaction.providerTime());
        entity.setCreatedAt(transaction.createdAt());
        entity.setPerformedAt(transaction.performedAt());
        entity.setCancelledAt(transaction.cancelledAt());
        entity.setReason(transaction.reason());
        return entity;
    }
}
