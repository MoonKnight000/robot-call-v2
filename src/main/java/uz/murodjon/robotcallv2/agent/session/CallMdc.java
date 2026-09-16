package uz.murodjon.robotcallv2.agent.session;

import org.slf4j.MDC;

/**
 * Puts a call's channel id in the logging context, so every line it produces carries
 * {@code [call:<channelId>]} (see {@code logback-spring.xml}).
 *
 * <p>Shared because almost nothing about a call runs on the thread that started it. ARI
 * events arrive on the event stream, a realtime engine's messages on its WebSocket
 * thread, and each tool body on a virtual thread of its own — and the MDC is a plain
 * thread-local that none of them inherit. Without this a whole call logged as
 * {@code [call:none]}, which defeats the only thing the marker is for: pulling one
 * conversation out of the dozens running beside it.
 */
public final class CallMdc {

    private static final String KEY = "channelId";

    private CallMdc() {
    }

    /**
     * Runs {@code body} with {@code channelId} in the context, restoring whatever was
     * there before.
     *
     * <p>Restoring rather than clearing, because these nest: a provider wraps its socket
     * callback and the dialog engine wraps the listener call inside it. Clearing on the
     * way out of the inner one would leave the rest of the outer block logging as
     * {@code none}.
     */
    public static void with(String channelId, Runnable body) {
        String previous = MDC.get(KEY);
        MDC.put(KEY, channelId);
        try {
            body.run();
        } finally {
            if (previous == null) {
                MDC.remove(KEY);
            } else {
                MDC.put(KEY, previous);
            }
        }
    }
}
