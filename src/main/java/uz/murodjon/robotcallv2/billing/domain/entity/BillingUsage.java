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
    /**
     * A fresh period: the plan's allowances, nothing used yet.
     *
     * <p>The used counters are zero and stay zero here — what a company has actually
     * spent is summed from its settled calls ({@code call_billing}), not from a counter
     * this row keeps. It used to start with invented usage, which is why every billing
     * screen showed the same numbers to every company.
     */
    public static BillingUsage defaultFor(long companyId, String period) {
        return new BillingUsage(
                null,
                companyId,
                period,
                0,
                5000,
                0L,
                2000000L,
                0L,
                1000000L,
                0,
                30,
                400.0,
                0.05,
                0.02,
                0L,
                Instant.now(),
                Instant.now()
        );
    }
}
