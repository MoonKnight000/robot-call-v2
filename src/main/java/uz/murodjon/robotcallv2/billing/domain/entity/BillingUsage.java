package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.Instant;

public record BillingUsage(
        Long id,
        long companyId,
        String billingPeriod,
        int usedMinutes,
        int limitMinutes,
        long usedTokens,
        long limitTokens,
        long usedTtsChars,
        long limitTtsChars,
        int usedChannels,
        int limitChannels,
        double overagePriceMinute,
        double overagePriceToken,
        double overagePriceTts,
        long totalSpendUzs,
        Instant createdAt,
        Instant updatedAt
) {
    public static BillingUsage defaultFor(long companyId, String period) {
        return new BillingUsage(
                null,
                companyId,
                period,
                3420,
                5000,
                1250000L,
                2000000L,
                420000L,
                1000000L,
                14,
                30,
                400.0,
                0.05,
                0.02,
                1450000L,
                Instant.now(),
                Instant.now()
        );
    }
}
