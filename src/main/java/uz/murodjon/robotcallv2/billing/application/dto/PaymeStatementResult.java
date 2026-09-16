package uz.murodjon.robotcallv2.billing.application.dto;

import java.util.List;

/** GetStatement's answer. */
public record PaymeStatementResult(
        List<PaymeStatementTransaction> transactions
) {
}
