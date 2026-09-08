package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.Instant;

/**
 * A company's plan and what it has left to spend.
 *
 * <p>{@code reservedUzs} is money held for calls that are dialled but not finished. It is
 * kept apart from the balance rather than deducted from it because a call's real cost is
 * only known when it ends: what a company can still commit to is
 * {@link #spendableUzs()}, while {@code balanceUzs} stays the money it actually has.
 */
public record CompanyBilling(
        Long id,
        long companyId,
        String planCode,
        String planName,
        long balanceUzs,
        long reservedUzs,
        boolean autoRecharge,
        Instant nextBillingDate,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * A company seen by billing for the first time: no money, no automatic top-ups.
     *
     * <p>It used to start with a balance and auto-recharge on, which made every billing
     * screen show numbers nobody had paid — the row exists to be filled in by a top-up,
     * not to flatter a demo.
     */
    public static CompanyBilling defaultFor(long companyId) {
        return new CompanyBilling(
                null,
                companyId,
                "PRO_MONTHLY",
                "Professional (Pro)",
                0L,
                0L,
                false,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    /** What the company can still commit to: its balance less what running calls hold. */
    public long spendableUzs() {
        return balanceUzs - reservedUzs;
    }
}
