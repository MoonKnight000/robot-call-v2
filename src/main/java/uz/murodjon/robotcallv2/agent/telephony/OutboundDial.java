package uz.murodjon.robotcallv2.agent.telephony;

import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;

/**
 * One outbound call, already checked (suspension, balance, do-not-call list) and ready for an
 * engine to place.
 *
 * @param targetId     the campaign target, or {@code CallAttempt.NO_TARGET}
 * @param number       the callee, normalised
 * @param language     the language the attempt row is written with
 * @param sipTrunkId   the trunk the caller asked for, or null for the company's default
 * @param outboundCall the campaign call to track, or null for a manual or test call
 * @param callId       a name for this call in logs and in the dial arguments
 */
public record OutboundDial(long companyId, long targetId, String number, String language, Long sipTrunkId,
                           OutboundCall outboundCall, String callId) {
}
