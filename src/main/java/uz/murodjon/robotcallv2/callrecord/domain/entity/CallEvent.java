package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;
import java.util.Map;

/** One step on a call's timeline (call_event). */
public record CallEvent(
        long id,
        long companyId,
        long callAttemptId,
        Instant at,
        CallEventType type,
        Map<String, Object> payload
) {
    public CallEvent {
        payload = payload == null ? Map.of() : payload;
    }
}
