package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kyutai Moshi Realtime Speech-to-Speech Engine Provider.
 *
 * <p>Moshi is a full-duplex conversational audio foundation model streaming 24 kHz
 * multi-stream audio frames over WebSocket with ultra-low latency (<200ms).
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.moshi.url:}'.isBlank() || !'${voice-agent.realtime.moshi.api-key:}'.isBlank()")
public class MoshiRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(MoshiRealtimeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int AUDIO_RATE = 24000;

    private final RealtimeProperties props;
    private volatile HttpClient client;

    public MoshiRealtimeProvider(RealtimeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        MoshiRealtimeProperties m = props.moshi();
        if (m == null) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(m.connectTimeoutSeconds()))
                .build();
        log.info("Moshi Realtime ready (url={}, model={}, rate={} Hz)", m.url(), m.model(), AUDIO_RATE);
    }

    @Override
    public String name() {
        return "moshi";
    }

    @Override
    public boolean supports(String language) {
        return true;
    }

    @Override
    public int inputSampleRate() {
        return AUDIO_RATE;
    }

    @Override
    public int outputSampleRate() {
        return AUDIO_RATE;
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        MoshiRealtimeProperties m = props.moshi();
        if (current == null || m == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "moshi", "not initialized");
        }

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);

        try {
            WebSocket.Builder wsBuilder = current.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(m.connectTimeoutSeconds()));
            if (m.apiKey() != null && !m.apiKey().isBlank()) {
                wsBuilder.header("Authorization", "Bearer " + m.apiKey());
            }

            WebSocket ws = wsBuilder.buildAsync(URI.create(m.url()), new MoshiResponseHandler(config.channelId(), listener, alive, ready))
                    .get(m.connectTimeoutSeconds(), TimeUnit.SECONDS);

            MoshiRealtimeSession session = new MoshiRealtimeSession(ws, alive);
            session.send(handshakeMessage(config, m));

            if (!ready.await(m.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_SETUP_FAILED, "moshi", m.connectTimeoutSeconds());
            }

            log.info("[{}] Moshi Realtime session open (model={})", config.channelId(), m.model());
            return session;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "moshi", e, e.getMessage());
        }
    }

    private static String handshakeMessage(RealtimeCallConfig config, MoshiRealtimeProperties m) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "handshake");
        root.put("model", config.modelOr(m.model()));
        root.put("sample_rate", AUDIO_RATE);
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            root.put("system_prompt", config.systemPrompt());
        }
        return root.toString();
    }

    private static final class MoshiResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder botTranscript = new StringBuilder();

        private MoshiResponseHandler(String channelId, RealtimeListener listener,
                                     AtomicBoolean alive, CountDownLatch ready) {
            this.channelId = channelId;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String json = message.toString();
                message.setLength(0);
                handle(json);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            short[] pcm = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            listener.onBotAudio(pcm);
            webSocket.request(1);
            return null;
        }

        private void handle(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String type = node.path("type").asText("");

                switch (type) {
                    case "ready", "handshake_ok" -> ready.countDown();
                    case "audio" -> {
                        String b64 = node.path("data").asText("");
                        if (!b64.isEmpty()) {
                            byte[] bytes = Base64.getDecoder().decode(b64);
                            short[] pcm = new short[bytes.length / 2];
                            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
                            listener.onBotAudio(pcm);
                        }
                    }
                    case "text" -> {
                        String token = node.path("text").asText("");
                        if (!token.isEmpty()) {
                            botTranscript.append(token);
                        }
                    }
                    case "turn_complete" -> {
                        if (botTranscript.length() > 0) {
                            listener.onOutputTranscript(botTranscript.toString());
                            botTranscript.setLength(0);
                        }
                        listener.onTurnComplete();
                    }
                    case "user_transcript" -> {
                        String userText = node.path("text").asText("");
                        if (!userText.isEmpty()) {
                            listener.onInputTranscript(userText, true);
                        }
                    }
                    case "interrupted" -> {
                        listener.onInterrupted();
                    }
                    default -> log.trace("[{}] Moshi event: {}", channelId, type);
                }
            } catch (Exception e) {
                log.warn("[{}] Unparseable Moshi message: {}", channelId, e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            ready.countDown();
            listener.onClosed(statusCode == WebSocket.NORMAL_CLOSURE ? null : new IllegalStateException(reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            ready.countDown();
            listener.onClosed(error);
        }
    }

    private static final class MoshiRealtimeSession implements RealtimeSession {

        private final WebSocket ws;
        private final AtomicBoolean alive;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;

        private MoshiRealtimeSession(WebSocket ws, AtomicBoolean alive) {
            this.ws = ws;
            this.alive = alive;
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (pcm == null || pcm.length == 0 || closed || !alive.get()) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asShortBuffer().put(pcm);
            synchronized (this) {
                sendChain = sendChain
                        .thenCompose(ignored -> ws.sendBinary(buffer, true))
                        .thenAccept(v -> {})
                        .exceptionally(e -> {
                            alive.set(false);
                            return null;
                        });
            }
        }

        @Override
        public void sendUserText(String text) {
            if (text == null || text.isBlank() || closed || !alive.get()) {
                return;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", "prompt");
            node.put("text", text);
            send(node.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            if (closed || !alive.get()) {
                return;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", "tool_result");
            node.put("call_id", callId);
            node.put("result", result != null ? result : "{}");
            send(node.toString());
        }

        private synchronized void send(String json) {
            if (closed || !alive.get()) {
                return;
            }
            sendChain = sendChain
                    .thenCompose(ignored -> ws.sendText(json, true))
                    .thenAccept(v -> {})
                    .exceptionally(e -> {
                        alive.set(false);
                        return null;
                    });
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            alive.set(false);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                    .exceptionally(e -> {
                        ws.abort();
                        return null;
                    });
        }
    }
}
