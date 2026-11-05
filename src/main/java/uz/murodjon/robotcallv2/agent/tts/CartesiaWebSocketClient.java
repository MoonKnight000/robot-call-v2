package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One shared Cartesia {@code /tts/websocket} connection, over which every line is generated as its
 * own <em>context</em>: the request carries a fresh {@code context_id}, and the server's
 * {@code chunk} messages (base64 PCM) come back tagged with it, ending in {@code done}. The point
 * is to skip the per-request HTTP exchange of {@code /tts/bytes}; whether that is worth anything
 * is for {@code voice.tts.ttfa{provider="cartesia"}} to say.
 *
 * <p>The connection is opened on demand and reopened when it has dropped (an idle socket is
 * closed by the server); a line that finds it dead is sent once more on a fresh one. A line that
 * stops early — an error, a timeout, the caller's interrupt — is cancelled with
 * {@code {"context_id", "cancel": true}} so the server stops spending on it.
 */
final class CartesiaWebSocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CartesiaWebSocketClient.class);

    /** Pushed to every waiting line when the socket goes away. */
    private static final String CLOSED = "__closed";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CartesiaTtsProperties properties;
    private final String apiVersion;
    private final Map<String, LinkedBlockingQueue<JsonNode>> contexts = new ConcurrentHashMap<>();
    private final Object connectLock = new Object();
    private final Object sendLock = new Object();
    private volatile WebSocket socket;

    CartesiaWebSocketClient(HttpClient httpClient, ObjectMapper objectMapper, CartesiaTtsProperties properties,
                            String apiVersion) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiVersion = apiVersion;
    }

    /**
     * Generate {@code body} (a complete generation request without its {@code context_id}) and hand
     * the PCM to {@code onChunk} as it arrives. Returns when the server says the context is done.
     */
    void generate(String contextId, ObjectNode body, PcmChunkListener onChunk) {
        LinkedBlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        contexts.put(contextId, events);
        boolean finished = false;
        try {
            body.put("context_id", contextId);
            sendWithReconnect(body.toString());
            long waitMs = TimeUnit.SECONDS.toMillis(properties.connectTimeoutSeconds());
            byte carry = 0;
            boolean carried = false;
            while (true) {
                JsonNode event = events.poll(waitMs, TimeUnit.MILLISECONDS);
                if (event == null) {
                    throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia",
                            "no message from the WebSocket for " + waitMs + " ms");
                }
                String type = event.path("type").asText("");
                if (CLOSED.equals(type)) {
                    throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia",
                            "the WebSocket closed mid-line");
                }
                if ("error".equals(type)) {
                    throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia",
                            event.path("status_code").asText("?") + " " + event.path("message").asText(
                                    event.path("title").asText("error")));
                }
                if ("chunk".equals(type) && event.hasNonNull("data")) {
                    byte[] raw = Base64.getDecoder().decode(event.get("data").asText());
                    if (carried) {
                        byte[] joined = new byte[raw.length + 1];
                        joined[0] = carry;
                        System.arraycopy(raw, 0, joined, 1, raw.length);
                        raw = joined;
                        carried = false;
                    }
                    if (raw.length % 2 == 1) {
                        carry = raw[raw.length - 1];
                        carried = true;
                        raw = Arrays.copyOf(raw, raw.length - 1);
                    }
                    if (raw.length > 0 && onChunk != null) {
                        onChunk.onChunk(toSamples(raw));
                    }
                }
                if ("done".equals(type) || event.path("done").asBoolean(false)) {
                    finished = true;
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, "interrupted");
        } finally {
            contexts.remove(contextId);
            if (!finished) {
                cancel(contextId);
            }
        }
    }

    private static short[] toSamples(byte[] raw) {
        short[] samples = new short[raw.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((raw[i * 2 + 1] << 8) | (raw[i * 2] & 0xFF));
        }
        return samples;
    }

    /** Sends on the live socket, or on a fresh one if it has dropped — once. */
    private void sendWithReconnect(String text) {
        try {
            send(connected(), text);
        } catch (RuntimeException first) {
            log.debug("Cartesia WebSocket send failed ({}), reconnecting", first.getMessage());
            invalidate();
            send(connected(), text);
        }
    }

    private void send(WebSocket target, String text) {
        try {
            synchronized (sendLock) {
                target.sendText(text, true).get(properties.connectTimeoutSeconds(), TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, "interrupted");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, e.getMessage());
        }
    }

    private void cancel(String contextId) {
        WebSocket current = socket;
        if (current == null) {
            return;
        }
        try {
            ObjectNode cancel = objectMapper.createObjectNode();
            cancel.put("context_id", contextId);
            cancel.put("cancel", true);
            // Not awaited: the line is already over, and a stalled socket must not hold the caller.
            synchronized (sendLock) {
                current.sendText(cancel.toString(), true);
            }
        } catch (RuntimeException e) {
            log.debug("Cartesia cancel not sent: {}", e.getMessage());
        }
    }

    private WebSocket connected() {
        WebSocket current = socket;
        if (current != null && !current.isOutputClosed() && !current.isInputClosed()) {
            return current;
        }
        synchronized (connectLock) {
            current = socket;
            if (current != null && !current.isOutputClosed() && !current.isInputClosed()) {
                return current;
            }
            try {
                // The API key goes in the Authorization header (the docs prefer it to the query
                // string); the version is given as the header and as the channel parameter, since
                // the reference does not say which of the two a WebSocket takes.
                current = httpClient.newWebSocketBuilder()
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .header("X-API-Key", properties.apiKey())
                        .header("Cartesia-Version", apiVersion)
                        .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                        .buildAsync(webSocketUri(), new Handler())
                        .get(properties.connectTimeoutSeconds(), TimeUnit.SECONDS);
                socket = current;
                log.info("Cartesia TTS WebSocket connected");
                return current;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, "interrupted");
            } catch (Exception e) {
                throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, e.getMessage());
            }
        }
    }

    /** {@code wss://<host of the configured REST url>/tts/websocket?cartesia_version=...}. */
    private URI webSocketUri() {
        URI rest = URI.create(properties.url());
        String scheme = "http".equalsIgnoreCase(rest.getScheme()) ? "ws" : "wss";
        String authority = rest.getRawAuthority();
        return URI.create(scheme + "://" + authority + "/tts/websocket?cartesia_version=" + apiVersion);
    }

    private void invalidate() {
        WebSocket stale = socket;
        socket = null;
        if (stale != null) {
            stale.abort();
        }
    }

    @Override
    public void close() {
        invalidate();
    }

    /** Routes each message to the line whose {@code context_id} it carries. */
    private final class Handler extends JsonWebSocketListener {

        @Override
        protected void onMessage(String json) {
            try {
                JsonNode message = objectMapper.readTree(json);
                LinkedBlockingQueue<JsonNode> line = contexts.get(message.path("context_id").asText(""));
                if (line != null) {
                    line.offer(message);
                } else if ("error".equals(message.path("type").asText(""))) {
                    log.warn("Cartesia WebSocket error outside any line: {}", json);
                }
            } catch (Exception e) {
                log.debug("Unreadable Cartesia WebSocket message: {}", e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            lost(webSocket);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Cartesia WebSocket error: {}", error.getMessage());
            lost(webSocket);
        }

        /** The socket is gone: forget it and wake every line waiting on it. */
        private void lost(WebSocket webSocket) {
            if (socket == webSocket) {
                socket = null;
            }
            ObjectNode closed = objectMapper.createObjectNode();
            closed.put("type", CLOSED);
            contexts.values().forEach(line -> line.offer(closed));
        }
    }
}
