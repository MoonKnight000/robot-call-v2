package uz.murodjon.robotcallv2.operator.domain.entity;

import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Instant;

/**
 * One operator's live state, as the queue dispatcher and the panel read it.
 *
 * @param reason            why, for BREAK ("tushlik", "javob bermadi"); null otherwise
 * @param since             when the current status began
 * @param channelId         the call ringing or connected to this operator, if any
 * @param lastCallEndedAt   when the operator last finished a call — LEAST_RECENT's key
 * @param callsToday        calls answered since midnight — FEWEST_CALLS' key
 * @param consecutiveMisses offers left ringing in a row; two sends the operator on BREAK
 * @param wrapUpUntil       when WRAP_UP ends by itself; null otherwise
 */
public record OperatorPresence(
        long userId,
        long companyId,
        OperatorStatus status,
        String reason,
        Instant since,
        String channelId,
        Instant lastCallEndedAt,
        int callsToday,
        int consecutiveMisses,
        Instant wrapUpUntil
) {

    public static OperatorPresence offline(long userId, long companyId, Instant now) {
        return new OperatorPresence(userId, companyId, OperatorStatus.OFFLINE, null, now, null, null, 0, 0, null);
    }

    public OperatorPresence withStatus(OperatorStatus newStatus, String newReason, Instant now, String newChannelId) {
        return new OperatorPresence(userId, companyId, newStatus, newReason, now, newChannelId,
                lastCallEndedAt, callsToday, consecutiveMisses, null);
    }

    public OperatorPresence afterCall(Instant now, Instant wrapUpEnds) {
        return new OperatorPresence(userId, companyId, OperatorStatus.WRAP_UP, null, now, null,
                now, callsToday, 0, wrapUpEnds);
    }

    public OperatorPresence answered(Instant now, String newChannelId) {
        return new OperatorPresence(userId, companyId, OperatorStatus.ON_CALL, null, now, newChannelId,
                lastCallEndedAt, callsToday + 1, 0, null);
    }

    public OperatorPresence missed(int misses, OperatorStatus newStatus, String newReason, Instant now) {
        return new OperatorPresence(userId, companyId, newStatus, newReason, now, null,
                lastCallEndedAt, callsToday, misses, null);
    }

    public OperatorPresence withCallsToday(int count) {
        return new OperatorPresence(userId, companyId, status, reason, since, channelId,
                lastCallEndedAt, count, consecutiveMisses, wrapUpUntil);
    }
}
