package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alibaba Cloud Qwen-Omni Realtime Speech-to-Speech Provider.
 *
 * <p>Qwen-Omni (Qwen2.5-Omni) provides end-to-end multimodal voice conversation
 * with native speech understanding and expressive speech synthesis over WebSocket.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.qwen-omni.api-key:}'.isBlank()")
@SuppressWarnings("deprecation")
public class QwenOmniRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenOmniRealtimeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int INPUT_RATE = 16000;
    private static final int OUTPUT_RATE = 24000;

    private final RealtimeProperties props;
    private volatile HttpClient client;

    public QwenOmniRealtimeProvider(RealtimeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        QwenOmniRealtimeProperties q = props.qwenOmni();
        if (q == null || q.apiKey() == null || q.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(q.connectTimeoutSeconds()))
                .build();
        log.info("Qwen-Omni Realtime ready (model={}, voice={}, in={}Hz, out={}Hz)",
                q.model(), q.voice(), INPUT_RATE, OUTPUT_RATE);
    }

    @Override
    public String name() {
        return "qwen-omni";
    }

    @Override
    public boolean supports(String language) {
        return true;
    }

    @Override
    public int inputSampleRate() {
        return INPUT_RATE;
    }

    @Override
    public int outputSampleRate() {
        return OUTPUT_RATE;
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        QwenOmniRealtimeProperties q = props.qwenOmni();
        if (current == null || q == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "qwen-omni", "not initialized");
        }

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);

        try {
            WebSocket ws = current.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + q.apiKey())
                    .header("X-DashScope-DataInspection", "enable")
                    .connectTimeout(Duration.ofSeconds(q.connectTimeoutSeconds()))
                    .buildAsync(URI.create(q.url()), new QwenOmniResponseHandler(config.channelId(), listener, alive, ready))
                    .get(q.connectTimeoutSeconds(), TimeUnit.SECONDS);

            QwenOmniRealtimeSession session = new QwenOmniRealtimeSession(ws, alive);
            session.send(initSessionMessage(config, q));

            if (!ready.await(q.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_SETUP_FAILED, "qwen-omni", q.connectTimeoutSeconds());
            }

            log.info("[{}] Qwen-Omni Realtime session open (model={}, voice={})",
                    config.channelId(), q.model(), q.voice());
            return session;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "qwen-omni", e, e.getMessage());
        }
    }

    private static String initSessionMessage(RealtimeCallConfig config, QwenOmniRealtimeProperties q) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("header", MAPPER.createObjectNode().put("action", "session-update"));
        ObjectNode payload = root.putObject("payload");
        payload.put("model", q.model());
        // The call's own voice wins; the configured one is the deployment default.
        payload.put("voice", config.voice() != null && !config.voice().isBlank()
                ? config.voice() : q.voice());
        payload.put("input_audio_format", "pcm16");
        payload.put("output_audio_format", "pcm16");
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            payload.put("instructions", config.systemPrompt());
        }

        List<ToolCallback> tools = config.tools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = payload.putArray("tools");
            for (ToolCallback tool : tools) {
                ObjectNode t = toolArray.addObject();
                t.put("type", "function");
                t.put("name", tool.getToolDefinition().name());
                t.put("description", tool.getToolDefinition().description());
                try {
                    t.set("parameters", MAPPER.readTree(tool.getToolDefinition().inputSchema()));
                } catch (Exception ignored) {
                }
            }
        }

        return root.toString();
    }

    private static final class QwenOmniResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder botTranscript = new StringBuilder();

        private QwenOmniResponseHandler(String channelId, RealtimeListener listener,
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
            message.append(StandardCharsets.UTF_8.decode(data));
            if (last) {
                String json = message.toString();
                message.setLength(0);
                handle(json);
            }
            webSocket.request(1);
            return null;
        }

        private void handle(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String event = node.path("header").path("event").asText("");

                switch (event) {
                    case "session-updated", "session-created" -> ready.countDown();
                    case "response.audio.delta" -> {
                        String b64 = node.path("payload").path("delta").asText("");
                        if (!b64.isEmpty()) {
                            byte[] bytes = Base64.getDecoder().decode(b64);
                            short[] pcm = new short[bytes.length / 2];
                            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
                            listener.onBotAudio(pcm);
                        }
                    }
                    case "response.text.delta" -> {
                        String text = node.path("payload").path("text").asText("");
                        if (!text.isEmpty()) {
                            botTranscript.append(text);
                        }
                    }
                    case "response.done" -> {
                        if (botTranscript.length() > 0) {
                            listener.onOutputTranscript(botTranscript.toString());
                            botTranscript.setLength(0);
                        }
                        listener.onTurnComplete();
                    }
                    case "input.transcription" -> {
                        String transcript = node.path("payload").path("text").asText("");
                        if (!transcript.isEmpty()) {
                            listener.onInputTranscript(transcript, true);
                        }
                    }
                    case "input.barge_in" -> {
                        listener.onInterrupted();
                    }
                    case "response.function_call" -> {
                        String callId = node.path("payload").path("call_id").asText("");
                        String name = node.path("payload").path("name").asText("");
                        String args = node.path("payload").path("arguments").asText("{}");
                        listener.onToolCall(callId, name, args);
                    }
                    default -> log.trace("[{}] Qwen-Omni event: {}", channelId, event);
                }
            } catch (Exception e) {
                log.warn("[{}] Unparseable Qwen-Omni message: {}", channelId, e.getMessage());
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

    private static final class QwenOmniRealtimeSession implements RealtimeSession {

        private final WebSocket ws;
        private final AtomicBoolean alive;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;

        private QwenOmniRealtimeSession(WebSocket ws, AtomicBoolean alive) {
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
            ObjectNode root = MAPPER.createObjectNode();
            root.put("header", MAPPER.createObjectNode().put("action", "append-audio"));
            root.putObject("payload").put("audio", Base64.getEncoder().encodeToString(buffer.array()));
            send(root.toString());
        }

        @Override
        public void sendUserText(String text) {
            if (text == null || text.isBlank() || closed || !alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("header", MAPPER.createObjectNode().put("action", "send-text"));
            root.putObject("payload").put("text", text);
            send(root.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            if (closed || !alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("header", MAPPER.createObjectNode().put("action", "submit-tool-output"));
            ObjectNode payload = root.putObject("payload");
            payload.put("call_id", callId);
            payload.put("output", result != null ? result : "{}");
            send(root.toString());
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
