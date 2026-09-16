package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.application.dto.PaymeAccount;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeCheckTransactionResult;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeReceiptDetail;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeReceiptItem;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeStatementTransaction;
import uz.murodjon.robotcallv2.billing.application.dto.PaymeTransactionResult;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.service.PaymeRules;

import java.time.Instant;
import java.util.List;

/** Provider transactions and top-ups in the shapes the Payme Merchant API answers with. */
@Component
public class PaymeMapper {

    /** Receipt type 0: a sale. */
    private static final int RECEIPT_SALE = 0;

    public PaymeTransactionResult toCreateResult(PaymentProviderTransaction transaction) {
        return new PaymeTransactionResult(transaction.createdAt().toEpochMilli(), null, null,
                String.valueOf(transaction.id()), PaymeRules.stateCode(transaction.state()));
    }

    public PaymeTransactionResult toPerformResult(PaymentProviderTransaction transaction) {
        return new PaymeTransactionResult(null, transaction.performedAt().toEpochMilli(), null,
                String.valueOf(transaction.id()), PaymeRules.stateCode(transaction.state()));
    }

    public PaymeTransactionResult toCancelResult(PaymentProviderTransaction transaction) {
        return new PaymeTransactionResult(null, null, transaction.cancelledAt().toEpochMilli(),
                String.valueOf(transaction.id()), PaymeRules.stateCode(transaction.state()));
    }

    public PaymeCheckTransactionResult toCheckResult(PaymentProviderTransaction transaction) {
        return new PaymeCheckTransactionResult(
                toMillis(transaction.createdAt()),
                toMillis(transaction.performedAt()),
                toMillis(transaction.cancelledAt()),
                String.valueOf(transaction.id()),
                PaymeRules.stateCode(transaction.state()),
                transaction.reason());
    }

    public PaymeStatementTransaction toStatementTransaction(PaymentProviderTransaction transaction) {
        return new PaymeStatementTransaction(
                transaction.providerTransactionId(),
                toMillis(transaction.providerTime()),
                transaction.amountTiyin(),
                new PaymeAccount(transaction.paymentId()),
                toMillis(transaction.createdAt()),
                toMillis(transaction.performedAt()),
                toMillis(transaction.cancelledAt()),
                String.valueOf(transaction.id()),
                PaymeRules.stateCode(transaction.state()),
                transaction.reason(),
                null);
    }

    /** The fiscal receipt for a top-up: one line, the whole amount. */
    public PaymeReceiptDetail toReceiptDetail(PaymentTopup topup, String ikpuCode, String packageCode,
                                              int vatPercent) {
        PaymeReceiptItem item = new PaymeReceiptItem("Balansni to'ldirish " + topup.paymentId(),
                topup.amountTiyin(), 1, ikpuCode, packageCode, vatPercent);
        return new PaymeReceiptDetail(RECEIPT_SALE, List.of(item));
    }

    /** Epoch milliseconds, 0 for a time that has not come — Payme's convention. */
    private static long toMillis(Instant at) {
        return at != null ? at.toEpochMilli() : 0L;
    }
}
