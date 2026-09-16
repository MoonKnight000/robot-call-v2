package uz.murodjon.robotcallv2.missedcall.domain.entity;

import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;

/**
 * One unserved call, as the call path reports it. Everything but the phone and the reason
 * is optional context for whoever calls back.
 */
public record MissedCallOccurrence(
        String phone,
        MissedCallReason reason,
        Long queueId,
        Long inboundRouteId,
        Long callAttemptId,
        Long voicemailMessageId
) {

    public MissedCallOccurrence withPhone(String normalizedPhone) {
        return new MissedCallOccurrence(normalizedPhone, reason, queueId, inboundRouteId, callAttemptId, voicemailMessageId);
    }
}
