package uz.murodjon.robotcallv2.billing.application.dto;

import java.time.Instant;

public record BillingOverviewResponse(
        String planName,
        String planCode,
        long balanceUzs,
        Instant nextBillingDate,
        boolean autoRecharge,
        BillingMetricsDto metrics
) {
}
