package uz.murodjon.robotcallv2.missedcall.domain.entity;

import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;

import java.time.Instant;

/**
 * A caller the company still owes a call to (docs/INBOUND-PBX-PLAN.md §8.2). One open row
 * per phone: ringing again adds to {@code callCount} and moves {@code lastAt}.
 *
 * @param reason             the latest reason the caller was not served
 * @param lastCallAttemptId  the most recent unserved call
 * @param voicemailMessageId the voicemail they left, if any
 * @param callbackAttempts   callbacks tried so far, by hand or automatically — automatic ones
 *                           stop at the limit, so an operator's own tries count against it
 */
public record MissedCall(
        long id,
        long companyId,
        String phone,
        MissedCallReason reason,
        MissedCallStatus status,
        int callCount,
        Instant firstAt,
        Instant lastAt,
        Long queueId,
        Long inboundRouteId,
        Long lastCallAttemptId,
        Long voicemailMessageId,
        Long assignedUserId,
        int callbackAttempts,
        Instant lastCallbackAt,
        Instant resolvedAt,
        Long resolvedByUserId,
        String note
) {
}
