package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;
import java.util.Map;

/**
 * One step on a call's timeline (call_event) — or, for the agent's trace types, on a chat
 * conversation's: exactly one of {@code callAttemptId} and {@code chatSessionId} is non-zero.
 */
public record CallEvent(
        long id,
        long companyId,
        long callAttemptId,
        long chatSessionId,
        Instant at,
        CallEventType type,
        Map<String, Object> payload
) {
    public CallEvent {
        payload = payload == null ? Map.of() : payload;
    }
}
