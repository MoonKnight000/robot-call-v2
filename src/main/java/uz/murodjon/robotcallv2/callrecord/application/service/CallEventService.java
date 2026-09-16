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
        if (callAttemptId == 0) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        try {
            repository.create(new CallEvent(0, companyId, callAttemptId, Instant.now(), type, payload));
        } catch (Exception e) {
            log.warn("Could not record {} for call {}: {}", type, callAttemptId, e.getMessage());
        }
    }

    @Override
    public List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId) {
        return repository.findByCallAttemptId(companyId, callAttemptId);
    }
}
