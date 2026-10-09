package uz.murodjon.robotcallv2.promise.domain.entity;

import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A promise to pay a call recorded, followed until it is kept or broken.
 *
 * @param targetId       the campaign target the call was for — what a broken promise is called back on;
 *                       null for an inbound or manual call
 * @param promisedAmount null when the caller named no amount
 * @param reminderSentAt when the reminder SMS went out; null — not (yet) sent
 * @param resolvedAt     when it left {@code OPEN}
 */
public record PaymentPromise(
        Long id,
        long companyId,
        long callAttemptId,
        Long targetId,
        String phone,
        LocalDate promisedDate,
        BigDecimal promisedAmount,
        PaymentPromiseStatus status,
        Instant reminderSentAt,
        Instant resolvedAt,
        Instant createdAt
) {

    /** A promise just recorded; the adapter stamps id and createdAt. */
    public static PaymentPromise opened(long companyId, long callAttemptId, Long targetId, String phone,
                                        LocalDate promisedDate, BigDecimal promisedAmount) {
        return new PaymentPromise(null, companyId, callAttemptId, targetId, phone, promisedDate, promisedAmount,
                PaymentPromiseStatus.OPEN, null, null, null);
    }
}
