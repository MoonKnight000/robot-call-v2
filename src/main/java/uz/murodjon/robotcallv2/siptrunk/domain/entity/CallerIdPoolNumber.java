package uz.murodjon.robotcallv2.siptrunk.domain.entity;

import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;

import java.time.Instant;

/**
 * A phone number member of a Caller ID pool.
 *
 * @param trunkId the SIP trunk of the registered phone number this member is ({@code phone_number.provider_trunk_id});
 *                a call presenting this number goes out on it. Null — no trunk yet, or a member V32 could not
 *                link to a number — and the member is never dialled from
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
        Instant createdAt,
        Long trunkId
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
