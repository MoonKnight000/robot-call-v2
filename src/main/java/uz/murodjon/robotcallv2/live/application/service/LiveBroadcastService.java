package uz.murodjon.robotcallv2.live.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fans out live events (§0.7) to every connected {@code GET /api/live/stream} client.
 */
@Service
public class LiveBroadcastService implements LiveUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcastService.class);

    private static final long HEARTBEAT_MS = 15_000;

    private final ConcurrentHashMap<Long, SseEmitter> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        long id = nextId.incrementAndGet();
        sessions.put(id, emitter);
        emitter.onCompletion(() -> sessions.remove(id));
        emitter.onTimeout(() -> sessions.remove(id));
        emitter.onError(e -> sessions.remove(id));
        try {
            emitter.send(SseEmitter.event().name("connected").data(id));
        } catch (IOException e) {
            sessions.remove(id);
        }
        return emitter;
    }

    /** Id of the most recent subscription, so {@link LiveKpiPublisher} can notice a new client. */
    public long lastSubscriptionId() {
        return nextId.get();
    }

    public void publish(LiveEventType type, Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        sessions.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(type.name()).data(payload));
            } catch (IOException | IllegalStateException e) {
                sessions.remove(id);
            }
        });
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        sessions.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                sessions.remove(id);
            }
        });
    }
}
