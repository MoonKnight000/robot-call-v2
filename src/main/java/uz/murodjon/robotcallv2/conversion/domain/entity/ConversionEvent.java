package uz.murodjon.robotcallv2.conversion.domain.entity;

import java.time.Instant;

/**
 * One thing that happened out in the world, as a company's own system reported it.
 *
 * <p>An event that matched no call is kept, marked {@code rejected} with a reason. Deleting
 * it would only mean the same webhook retry arrives as a new event tomorrow, and "we were
 * told, and nothing was in the window" is worth being able to read back.
 *
 * @param phone      the number this is about; matched against the numbers we dialled
 * @param valueUzs   what it was worth, or null for a goal that has no money attached
 * @param dedupeKey  the poster's own id for the thing — what makes a retry a no-op
 * @param evidence   whatever the poster wants kept, as JSON: an order id, a receipt
 */
public record ConversionEvent(
        Long id,
        long companyId,
        String goalKey,
        String phone,
        Instant occurredAt,
        Long valueUzs,
        String source,
        String evidence,
        String dedupeKey,
        boolean rejected,
        String rejectionReason,
        Instant ingestedAt
) {
    /** An event as posted, before anything has decided whether a call earned it. */
    public static ConversionEvent reported(long companyId, String goalKey, String phone, Instant occurredAt,
                                           Long valueUzs, String source, String evidence, String dedupeKey) {
        return new ConversionEvent(null, companyId, goalKey, phone, occurredAt, valueUzs, source, evidence,
                dedupeKey, false, null, null);
    }

    /** The same event, recorded as matching nothing we can credit. */
    public ConversionEvent reject(String reason) {
        return new ConversionEvent(id, companyId, goalKey, phone, occurredAt, valueUzs, source, evidence,
                dedupeKey, true, reason, ingestedAt);
    }
}
