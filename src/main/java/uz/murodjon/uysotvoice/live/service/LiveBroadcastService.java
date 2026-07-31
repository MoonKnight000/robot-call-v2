package uz.murodjon.uysotvoice.live.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import uz.murodjon.uysotvoice.live.dto.LiveEventType;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fans out live events (§0.7) to every connected {@code GET /api/live/stream} client.
 *
 * <p>Deliberately a flat {@code Map<id, SseEmitter>} rather than a Reactor {@code Sinks.Many}
 * — the rest of this codebase is plain imperative Spring MVC, not WebFlux, and this keeps
 * the live channel readable the same way.
 *
 * <p>Every mutation to {@link #sessions} the source data can trigger (a new transcript
 * line, a KPI tick) calls {@link #publish}; nothing here computes or stores that data
 * itself, so there is exactly one source of truth for call state.
 */
@Service
public class LiveBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcastService.class);

    /** Comment-only ping so idle-connection-killing proxies don't drop the stream. */
    private static final long HEARTBEAT_MS = 15_000;

    private final ConcurrentHashMap<Long, SseEmitter> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    /** Opens a new subscription. Never times out — the client's own disconnect ends it. */
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

    /** Sends {@code payload} as a {@code type}-named event to every connected client. */
    public void publish(LiveEventType type, Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        sessions.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(type.name()).data(payload));
            } catch (IOException | IllegalStateException e) {
                // A dead connection the completion/error callback has not caught up with
                // yet — remove it now rather than fail every future publish on it too.
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
