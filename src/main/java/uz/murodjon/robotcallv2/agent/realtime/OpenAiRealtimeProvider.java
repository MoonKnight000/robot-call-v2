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
 * OpenAI Realtime API Provider over WebSocket (GPT-4o Realtime / GPT-4o-mini Realtime).
 *
 * <p>Delivers true full-duplex Speech-to-Speech (S2S) multimodal streaming with
 * integrated server-side VAD, 24kHz PCM audio, function calling, and sub-second
 * conversational turnaround.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.open-ai.api-key:}'.isBlank()")
@SuppressWarnings("deprecation")
public class OpenAiRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int AUDIO_RATE = 24000;

    private final RealtimeProperties props;
    private volatile HttpClient client;

    public OpenAiRealtimeProvider(RealtimeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        OpenAiRealtimeProperties openAi = props.openAi();
        if (openAi == null || openAi.apiKey() == null || openAi.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(openAi.connectTimeoutSeconds()))
                .build();
        log.info("OpenAI Realtime ready (model={}, voice={}, rate={} Hz)",
                openAi.model(), openAi.voice(), AUDIO_RATE);
    }

    @Override
    public String name() {
        return "openai-realtime";
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
        OpenAiRealtimeProperties openAi = props.openAi();
        if (current == null || openAi == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED,
                    "openai-realtime", "not initialized");
        }
        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);

        String wsUrl = openAi.url();
        if (!wsUrl.contains("model=")) {
            wsUrl += (wsUrl.contains("?") ? "&" : "?") + "model=" + openAi.model();
        }
        URI uri = URI.create(wsUrl);

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + openAi.apiKey())
                    .header("OpenAI-Beta", "realtime=v1")
                    .connectTimeout(Duration.ofSeconds(openAi.connectTimeoutSeconds()))
                    .buildAsync(uri, new OpenAiResponseHandler(config.channelId(), listener, alive, ready))
                    .get(openAi.connectTimeoutSeconds(), TimeUnit.SECONDS);

            OpenAiRealtimeSession session = new OpenAiRealtimeSession(webSocket, alive);
            session.send(sessionUpdateMessage(config, openAi));

            if (!ready.await(openAi.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_SETUP_FAILED,
                        "openai-realtime", openAi.connectTimeoutSeconds());
            }

            log.info("[{}] OpenAI Realtime session open (model={}, voice={})",
                    config.channelId(), openAi.model(), openAi.voice());
            return session;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED,
                    "openai-realtime", e, e.getMessage());
        }
    }

    private static String sessionUpdateMessage(RealtimeCallConfig config, OpenAiRealtimeProperties openAi) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "session.update");
        ObjectNode session = root.putObject("session");

        session.putArray("modalities").add("audio").add("text");
        // The call's own voice wins; the configured one is the deployment default.
        session.put("voice", config.voice() != null && !config.voice().isBlank()
                ? config.voice() : openAi.voice());
        session.put("input_audio_format", "pcm16");
        session.put("output_audio_format", "pcm16");
        session.put("temperature", openAi.temperature());

        ObjectNode turnDetection = session.putObject("turn_detection");
        turnDetection.put("type", "server_vad");
        turnDetection.put("threshold", 0.5);
        turnDetection.put("prefix_padding_ms", 300);
        turnDetection.put("silence_duration_ms", 400);

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            session.put("instructions", config.systemPrompt());
        }

        ObjectNode inputTrans = session.putObject("input_audio_transcription");
        inputTrans.put("model", "whisper-1");

        List<ToolCallback> tools = config.tools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = session.putArray("tools");
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

    private static final class OpenAiResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder botTranscript = new StringBuilder();

        private OpenAiResponseHandler(String channelId, RealtimeListener listener,
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
                String type = node.path("type").asText("");

                switch (type) {
                    case "session.created", "session.updated" -> ready.countDown();
                    case "response.audio.delta" -> {
                        String delta = node.path("delta").asText("");
                        if (!delta.isEmpty()) {
                            listener.onBotAudio(toPcm(delta));
                        }
                    }
                    case "response.audio_transcript.delta" -> {
                        String text = node.path("delta").asText("");
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
                    case "conversation.item.input_audio_transcription.completed" -> {
                        String transcript = node.path("transcript").asText("");
                        if (!transcript.isEmpty()) {
                            listener.onInputTranscript(transcript, true);
                        }
                    }
                    case "input_audio_buffer.speech_started" -> {
                        listener.onInterrupted();
                    }
                    case "response.function_call_arguments.done" -> {
                        String callId = node.path("call_id").asText("");
                        String name = node.path("name").asText("");
                        String args = node.path("arguments").asText("{}");
                        listener.onToolCall(callId, name, args);
                    }
                    case "error" -> {
                        log.warn("[{}] OpenAI Realtime error: {}", channelId, node.path("error").path("message").asText());
                    }
                    default -> log.trace("[{}] OpenAI Realtime event: {}", channelId, type);
                }
            } catch (Exception e) {
                log.warn("[{}] Unparseable OpenAI event: {}", channelId, e.getMessage());
            }
        }

        private static short[] toPcm(String base64) {
            byte[] bytes = Base64.getDecoder().decode(base64);
            short[] pcm = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            return pcm;
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

    private static final class OpenAiRealtimeSession implements RealtimeSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;

        private OpenAiRealtimeSession(WebSocket webSocket, AtomicBoolean alive) {
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
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "input_audio_buffer.append");
            root.put("audio", Base64.getEncoder().encodeToString(buffer.array()));
            send(root.toString());
        }

        @Override
        public void sendUserText(String text) {
            if (text == null || text.isBlank() || closed || !alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "conversation.item.create");
            ObjectNode item = root.putObject("item");
            item.put("type", "message");
            item.put("role", "user");
            ArrayNode content = item.putArray("content");
            ObjectNode part = content.addObject();
            part.put("type", "input_text");
            part.put("text", text);
            send(root.toString());

            ObjectNode createResp = MAPPER.createObjectNode();
            createResp.put("type", "response.create");
            send(createResp.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            if (closed || !alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "conversation.item.create");
            ObjectNode item = root.putObject("item");
            item.put("type", "function_call_output");
            item.put("call_id", callId);
            item.put("output", result != null ? result : "{}");
            send(root.toString());

            ObjectNode createResp = MAPPER.createObjectNode();
            createResp.put("type", "response.create");
            send(createResp.toString());
        }

        private synchronized void send(String json) {
            if (closed || !alive.get()) {
                return;
            }
            sendChain = sendChain
                    .thenCompose(ignored -> webSocket.sendText(json, true))
                    .thenAccept(sent -> {
                    })
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
