package uz.murodjon.robotcallv2.callrecord.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A call's result recorded a promise to pay with a date. Published once per result write; the
 * outbox may write the same call's result twice, so a listener keeps one promise per call.
 *
 * @param targetId       the campaign target the call was for, null for an inbound or manual call
 * @param promisedAmount null when the caller named no amount
 */
public record PaymentPromisedEvent(
        long companyId,
        long callAttemptId,
        Long targetId,
        String phone,
        LocalDate promisedDate,
        BigDecimal promisedAmount
) {
}
