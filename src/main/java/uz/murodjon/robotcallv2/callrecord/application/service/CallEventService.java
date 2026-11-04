package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallEventRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A call's timeline — which flow nodes it went through, the keys pressed, queueing, the
 * operators it rang. Written from the call path, so a failure is logged and swallowed:
 * a missing timeline row must never cost a caller their call.
 */
@Service
public class CallEventService implements CallEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallEventService.class);

    private final CallEventRepository repository;

    public CallEventService(CallEventRepository repository) {
        this.repository = repository;
    }

    /** Records one event; {@code keyValues} alternate key, value ("queueId", 3, "position", 2). */
    @Override
    public void record(long companyId, long callAttemptId, CallEventType type, Object... keyValues) {
        recordAt(companyId, callAttemptId, type, Instant.now(), keyValues);
    }

    @Override
    public void recordAt(long companyId, long callAttemptId, CallEventType type, Instant at, Object... keyValues) {
        if (callAttemptId == 0) {
            return;
        }
        write(new CallEvent(0, companyId, callAttemptId, 0, at, type, payloadOf(keyValues)));
    }

    @Override
    public void recordForChat(long companyId, long chatSessionId, CallEventType type, Object... keyValues) {
        if (chatSessionId == 0) {
            return;
        }
        write(new CallEvent(0, companyId, 0, chatSessionId, Instant.now(), type, payloadOf(keyValues)));
    }

    @Override
    public List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId) {
        return repository.findByCallAttemptId(companyId, callAttemptId);
    }

    @Override
    public List<CallEvent> findByChatSessionId(long companyId, long chatSessionId) {
        return repository.findByChatSessionId(companyId, chatSessionId);
    }

    private static Map<String, Object> payloadOf(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return payload;
    }

    private void write(CallEvent event) {
        try {
            repository.create(event);
        } catch (Exception e) {
            log.warn("Could not record {} for call {} / chat {}: {}", event.type(), event.callAttemptId(),
                    event.chatSessionId(), e.getMessage());
        }
    }
}
