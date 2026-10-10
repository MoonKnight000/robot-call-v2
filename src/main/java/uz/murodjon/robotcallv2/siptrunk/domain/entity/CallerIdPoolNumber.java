package uz.murodjon.robotcallv2.siptrunk.domain.entity;

import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;

import java.time.Instant;

/**
 * A phone number member of a Caller ID pool.
 */
public record CallerIdPoolNumber(
        long id,
        long poolId,
        String phone,
        String label,
        long usageCount,
        long answerCount,
        CallerIdNumberStatus status,
        Instant coolingUntil,
        Instant lastUsedAt,
        Instant createdAt
) {

    public double answerRate() {
        if (usageCount == 0) {
            return 0.0;
        }
        return (double) answerCount / usageCount;
    }

    public boolean isEligible(Instant now) {
        if (status == CallerIdNumberStatus.DISABLED) {
            return false;
        }
        if (status == CallerIdNumberStatus.COOLING_DOWN) {
            return coolingUntil != null && now.isAfter(coolingUntil);
        }
        return true;
    }
}
