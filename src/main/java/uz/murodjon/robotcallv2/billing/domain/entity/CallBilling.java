package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;

import java.time.Instant;

/**
 * One call's charge: what was held before it was dialled, what it consumed, and what that
 * came to at the price list it was charged against.
 *
 * <p>{@code rateVersion} is the reason a row here can still be explained a year later —
 * the prices in {@code config/billing.yml} move, and without the version stamped on the
 * row an old call would silently reprice itself every time they did.
 *
 * @param id              storage's, null before it is written
 * @param callAttemptId   the call this charge belongs to, null while the hold is still open
 * @param targetId        what the hold was taken for, so a call can find its own hold
 * @param companyId       whose balance it moves
 * @param status          where the charge has got to
 * @param rateVersion     the price list this was charged against
 * @param reservedUzs     what was held before dialling
 * @param usage           what the call consumed; zero until it is settled
 * @param cost            the same usage priced; zero until it is settled
 * @param createdAt       storage's to stamp
 * @param settledAt       when it was charged, or null while it is still held
 */
public record CallBilling(
        Long id,
        Long callAttemptId,
        Long targetId,
        long companyId,
        CallBillingStatus status,
        String rateVersion,
        long reservedUzs,
        CallUsage usage,
        CallCostBreakdown cost,
        Instant createdAt,
        Instant settledAt
) {
    /** The hold taken for a target about to be dialled; no call exists for it yet. */
    public static CallBilling heldFor(long targetId, long companyId, String rateVersion, long reservedUzs) {
        return new CallBilling(null, null, targetId, companyId, CallBillingStatus.RESERVED, rateVersion,
                reservedUzs, CallUsage.NONE, CallCostBreakdown.FREE, null, null);
    }

    /** A call charged with no hold behind it — an inbound call, or a manual one. */
    public static CallBilling unheld(long callAttemptId, long companyId, String rateVersion) {
        return new CallBilling(null, callAttemptId, null, companyId, CallBillingStatus.RESERVED, rateVersion,
                0, CallUsage.NONE, CallCostBreakdown.FREE, null, null);
    }

    /** The hold, now attached to the call that spent it and charged for what it consumed. */
    public CallBilling settle(long spentByCallAttemptId, CallUsage measured, CallCostBreakdown priced,
                              String rateVersion) {
        return new CallBilling(id, spentByCallAttemptId, targetId, companyId, CallBillingStatus.SETTLED,
                rateVersion, reservedUzs, measured, priced, createdAt, Instant.now());
    }

    /** The hold given back: the number was never dialled, so there is nothing to charge. */
    public CallBilling release() {
        return new CallBilling(id, callAttemptId, targetId, companyId, CallBillingStatus.RELEASED, rateVersion,
                reservedUzs, CallUsage.NONE, CallCostBreakdown.FREE, createdAt, Instant.now());
    }
}
