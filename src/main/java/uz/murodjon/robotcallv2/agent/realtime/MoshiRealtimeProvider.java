package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
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
 *
 * <p><b>No tool protocol.</b> The Moshi socket takes audio, a prompt and text; it has no
 * way to declare a function or to call one. An agent on it could not end the call, transfer
 * it or read a call fact, so {@link #lacksToolCalling()} is true: saving an agent on this
 * engine is refused and a call that still resolves to it runs on the cascade pipeline.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.moshi.url:}'.isBlank() || !'${voice-agent.realtime.moshi.api-key:}'.isBlank()")
public class MoshiRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(MoshiRealtimeProvider.class);
    private final ObjectMapper objectMapper;

    private static final int AUDIO_RATE = 24000;

    private final RealtimeProperties realtimeProperties;
    private volatile HttpClient client;

    public MoshiRealtimeProvider(RealtimeProperties realtimeProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.realtimeProperties = realtimeProperties;
    }

    @PostConstruct
    public void init() {
        MoshiRealtimeProperties m = realtimeProperties.moshi();
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
    public String resolveModel(RealtimeCallConfig config) {
        MoshiRealtimeProperties m = realtimeProperties.moshi();
        return config.modelOr(m == null ? null : m.model());
    }

    @Override
    public boolean lacksToolCalling() {
        return true;
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        MoshiRealtimeProperties m = realtimeProperties.moshi();
        if (current == null || m == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_NOT_INITIALIZED, "moshi", "moshi");
        }

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);

        try {
            WebSocket.Builder wsBuilder = current.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(m.connectTimeoutSeconds()));
            if (m.apiKey() != null && !m.apiKey().isBlank()) {
                wsBuilder.header("Authorization", "Bearer " + m.apiKey());
            }

            WebSocket webSocket = wsBuilder.buildAsync(URI.create(m.url()), new MoshiResponseHandler(config.channelId(), listener, alive, ready))
                    .get(m.connectTimeoutSeconds(), TimeUnit.SECONDS);

            MoshiRealtimeSession session = new MoshiRealtimeSession(webSocket, alive);
            session.send(handshakeMessage(config, m));

            if (!ready.await(m.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_SETUP_FAILED, "moshi", "moshi",
                        m.connectTimeoutSeconds());
            }

            log.info("[{}] Moshi Realtime session open (model={})", config.channelId(), m.model());
            return session;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_CONNECT_FAILED, "moshi", e, "moshi", e.getMessage());
        }
    }

    private String handshakeMessage(RealtimeCallConfig config, MoshiRealtimeProperties m) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "handshake");
        root.put("model", config.modelOr(m.model()));
        root.put("sample_rate", AUDIO_RATE);
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            root.put("system_prompt", config.systemPrompt());
        }
        return root.toString();
    }

    private final class MoshiResponseHandler extends JsonWebSocketListener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder botTranscript = new StringBuilder();

        private MoshiResponseHandler(String channelId, RealtimeListener listener,
                                     AtomicBoolean alive, CountDownLatch ready) {
            this.channelId = channelId;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
        }

        @Override
        protected void onBinaryFrame(ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            short[] pcm = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            listener.onBotAudio(pcm);
        }

        /**
         * Every message arrives on the WebSocket's own thread, which knows nothing about
         * the call — so the id goes in here, once, and everything below it (this class's
         * logs and the dialog engine's, reached through the listener) is filed under the
         * call it belongs to instead of {@code [call:none]}.
         */
        @Override
        protected void onMessage(String json) {
            CallMdc.with(channelId, () -> dispatch(json));
        }

        private void dispatch(String json) {
            try {
                JsonNode node = objectMapper.readTree(json);
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

    private final class MoshiRealtimeSession implements RealtimeSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;

        private MoshiRealtimeSession(WebSocket webSocket, AtomicBoolean alive) {
            this.webSocket = webSocket;
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
                        .thenCompose(ignored -> webSocket.sendBinary(buffer, true))
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
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "prompt");
            node.put("text", text);
            send(node.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            if (closed || !alive.get()) {
                return;
            }
            ObjectNode node = objectMapper.createObjectNode();
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
                    .thenCompose(ignored -> webSocket.sendText(json, true))
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
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                    .exceptionally(e -> {
                        webSocket.abort();
                        return null;
                    });
        }
    }
}
