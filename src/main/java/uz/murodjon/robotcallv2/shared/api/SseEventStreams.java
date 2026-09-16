package uz.murodjon.robotcallv2.shared.api;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.io.IOException;
import java.util.function.Function;

/**
 * Serves a push subscription as a server-sent-events response. The {@link SseEmitter} stays
 * in the web layer; the service only ever sees an {@link EventSink}. Once the client is gone —
 * the response completed, timed out or failed — the subscription is cancelled.
 */
public final class SseEventStreams {

    /** No server-side timeout: the stream lives as long as its client stays connected. */
    private static final long NO_TIMEOUT = 0L;
    /** Sent as an SSE comment ({@code :ping}), which {@code EventSource} ignores. */
    private static final String HEARTBEAT_COMMENT = "ping";

    private SseEventStreams() {
    }

    /** {@code subscribe} opens the subscription against the sink it is handed. */
    public static SseEmitter open(Function<EventSink, EventSubscription> subscribe) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        EventSubscription subscription = subscribe.apply(new SseEventSink(emitter));
        emitter.onCompletion(subscription::cancel);
        emitter.onTimeout(subscription::cancel);
        emitter.onError(error -> subscription.cancel());
        return emitter;
    }

    private record SseEventSink(SseEmitter emitter) implements EventSink {

        @Override
        public void send(String eventName, Object payload) throws IOException {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        }

        @Override
        public void sendHeartbeat() throws IOException {
            emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
        }
    }
}
