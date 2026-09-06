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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pipecat Cloud & Anthropic Claude Realtime Voice Provider.
 *
 * <p>Connects to Pipecat voice agents powered by Anthropic Claude (e.g. Claude 3.5 Haiku,
 * Claude 3.5 Sonnet) running on Pipecat Cloud or a self-hosted Pipecat WebSocket gateway.
 * Delivers full-duplex conversational audio streaming with client-side/server-side VAD,
 * interruption handling, and tool execution.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.pipecat.api-key:}'.isBlank() || !'${voice-agent.realtime.pipecat.ws-url:}'.isBlank() || !'${voice-agent.realtime.pipecat.url:}'.isBlank()")
public class PipecatRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(PipecatRealtimeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RealtimeProperties realtimeProperties;
    private volatile HttpClient client;

    public PipecatRealtimeProvider(RealtimeProperties realtimeProperties) {
        this.realtimeProperties = realtimeProperties;
    }

    @PostConstruct
    public void init() {
        PipecatRealtimeProperties p = realtimeProperties.pipecat();
        if (p == null) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(p.connectTimeoutSeconds()))
                .build();
        log.info("Pipecat Realtime ready (agent={}, model={}, rate={} Hz, url={})",
                p.agentName(), p.model(), p.sampleRate(), p.url());
    }

    @Override
    public String name() {
        return "pipecat";
    }

    @Override
    public boolean supports(String language) {
        return true;
    }

    @Override
    public int inputSampleRate() {
        PipecatRealtimeProperties p = realtimeProperties.pipecat();
        return p != null ? p.sampleRate() : 16000;
    }

    @Override
    public int outputSampleRate() {
        PipecatRealtimeProperties p = realtimeProperties.pipecat();
        return p != null ? p.sampleRate() : 16000;
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        PipecatRealtimeProperties p = realtimeProperties.pipecat();
        if (current == null || p == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "pipecat", "not initialized");
        }

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);

        String targetWsUrl = resolveWebSocketUrl(p, current, config.channelId(), config);

        try {
            WebSocket.Builder wsBuilder = current.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(p.connectTimeoutSeconds()));

            if (p.apiKey() != null && !p.apiKey().isBlank()) {
                wsBuilder.header("Authorization", "Bearer " + p.apiKey());
            }

            WebSocket ws = wsBuilder.buildAsync(URI.create(targetWsUrl),
                            new PipecatResponseHandler(config.channelId(), listener, alive, ready))
                    .get(p.connectTimeoutSeconds(), TimeUnit.SECONDS);

            PipecatRealtimeSession session = new PipecatRealtimeSession(ws, alive);
            session.send(handshakeMessage(config, p));

            // Wait for ready event with timeout, or proceed if connection is open
            boolean welcomed = ready.await(Math.min(p.connectTimeoutSeconds(), 5), TimeUnit.SECONDS);
            if (!welcomed) {
                log.debug("[{}] Pipecat ready event not received within initial timeout; proceeding on open socket", config.channelId());
            }

            String effectiveModel = config.modelOr(config.pipecatLlm() != null && !config.pipecatLlm().isBlank() ? config.pipecatLlm() : p.model());
            log.info("[{}] Pipecat Realtime session open (agent={}, model={}, stt={}, tts={})",
                    config.channelId(), p.agentName(), effectiveModel, config.pipecatStt(), config.pipecatTts());
            return session;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED, "pipecat", e, e.getMessage());
        }
    }

    private String resolveWebSocketUrl(PipecatRealtimeProperties p, HttpClient httpClient,
                                       String channelId, RealtimeCallConfig config) {
        if (p.wsUrl() != null && !p.wsUrl().isBlank()) {
            return p.wsUrl();
        }

        // If an HTTP Pipecat Cloud start endpoint is given with an API key, invoke the session start endpoint
        if (p.url().startsWith("http") && p.apiKey() != null && !p.apiKey().isBlank()) {
            try {
                String startUrl = p.url().replaceAll("/+$", "") + "/" + p.agentName() + "/start";
                ObjectNode payload = MAPPER.createObjectNode();
                payload.put("createDailyRoom", true);
                payload.put("channelId", channelId);
                if (config.pipecatStt() != null && !config.pipecatStt().isBlank()) {
                    payload.put("stt", config.pipecatStt());
                }
                // Scenario override first, then the company's sub-engine — same rule the
                // handshake below uses, so the room and the session agree on the model.
                String llm = config.modelOr(config.pipecatLlm());
                if (llm != null && !llm.isBlank()) {
                    payload.put("llm", llm);
                }
                if (config.pipecatTts() != null && !config.pipecatTts().isBlank()) {
                    payload.put("tts", config.pipecatTts());
                }

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(startUrl))
                        .timeout(Duration.ofSeconds(p.connectTimeoutSeconds()))
                        .header("Authorization", "Bearer " + p.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    JsonNode respNode = MAPPER.readTree(resp.body());
                    if (respNode.has("ws_url")) {
                        return respNode.path("ws_url").asText();
                    }
                } else {
                    log.warn("[{}] Pipecat Cloud start returned HTTP {}: {}", channelId, resp.statusCode(), resp.body());
                }
            } catch (Exception e) {
                log.warn("[{}] Failed to start Pipecat Cloud dynamic session via REST: {}", channelId, e.getMessage());
            }
        }

        // Fallback: derive websocket url from base url
        String url = p.url();
        if (url.startsWith("https://")) {
            return "wss://" + url.substring("https://".length()) + "/ws";
        } else if (url.startsWith("http://")) {
            return "ws://" + url.substring("http://".length()) + "/ws";
        }
        return url;
    }

    private String handshakeMessage(RealtimeCallConfig config, PipecatRealtimeProperties p) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "session_init");
        root.put("agent", p.agentName());
        String selectedModel = config.modelOr(config.pipecatLlm() != null && !config.pipecatLlm().isBlank() ? config.pipecatLlm() : p.model());
        String selectedStt = config.pipecatStt() != null && !config.pipecatStt().isBlank() ? config.pipecatStt() : "deepgram";
        String selectedTts = config.pipecatTts() != null && !config.pipecatTts().isBlank() ? config.pipecatTts() : "cartesia";
        root.put("model", selectedModel);
        root.put("stt", selectedStt);
        root.put("tts", selectedTts);
        root.put("sample_rate", p.sampleRate());
        root.put("language", config.language());
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            root.put("instructions", config.systemPrompt());
        }
        if (config.voice() != null && !config.voice().isBlank()) {
            root.put("voice", config.voice());
        }
        return root.toString();
    }

    private static final class PipecatResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder botTranscript = new StringBuilder();

        private PipecatResponseHandler(String channelId, RealtimeListener listener,
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
                String type = node.path("type").asText(node.path("event").asText(""));

                switch (type) {
                    case "ready", "handshake_ok", "session_started", "session_created" -> ready.countDown();
                    case "audio" -> {
                        String b64 = node.path("data").asText(node.path("audio").asText(""));
                        if (!b64.isEmpty()) {
                            byte[] bytes = Base64.getDecoder().decode(b64);
                            short[] pcm = new short[bytes.length / 2];
                            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
                            listener.onBotAudio(pcm);
                        }
                    }
                    case "text", "transcript", "bot_transcript" -> {
                        String text = node.path("text").asText(node.path("content").asText(""));
                        String role = node.path("role").asText("assistant");
                        if (!text.isEmpty()) {
                            if ("user".equalsIgnoreCase(role)) {
                                listener.onInputTranscript(text, true);
                            } else {
                                botTranscript.append(text);
                                listener.onOutputTranscript(text);
                            }
                        }
                    }
                    case "turn_complete", "bot_stopped_speaking" -> {
                        botTranscript.setLength(0);
                        listener.onTurnComplete();
                    }
                    case "user_transcript", "input_transcript" -> {
                        String userText = node.path("text").asText("");
                        if (!userText.isEmpty()) {
                            listener.onInputTranscript(userText, true);
                        }
                    }
                    case "interrupted", "barge_in", "user_interruption" -> {
                        listener.onInterrupted();
                    }
                    case "tool_call", "function_call" -> {
                        String callId = node.path("call_id").asText(node.path("id").asText("call_1"));
                        String fnName = node.path("name").asText(node.path("function").path("name").asText(""));
                        String args = node.path("arguments").asText(node.path("function").path("arguments").asText("{}"));
                        listener.onToolCall(callId, fnName, args);
                    }
                    default -> log.trace("[{}] Pipecat event: {}", channelId, type);
                }
            } catch (Exception e) {
                log.warn("[{}] Unparseable Pipecat message: {}", channelId, e.getMessage());
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

    private static final class PipecatRealtimeSession implements RealtimeSession {

        private final WebSocket ws;
        private final AtomicBoolean alive;

        private PipecatRealtimeSession(WebSocket ws, AtomicBoolean alive) {
            this.ws = ws;
            this.alive = alive;
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (!alive.get() || pcm == null || pcm.length == 0) {
                return;
            }
            ByteBuffer buf = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : pcm) {
                buf.putShort(s);
            }
            buf.flip();
            ws.sendBinary(buf, true);
        }

        @Override
        public void sendUserText(String text) {
            if (!alive.get() || text == null) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "user_text");
            root.put("text", text);
            send(root.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            if (!alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "tool_result");
            root.put("call_id", callId);
            root.put("result", result != null ? result : "");
            send(root.toString());
        }

        @Override
        public void close() {
            if (alive.compareAndSet(true, false)) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
                } catch (Exception ignored) {
                }
            }
        }

        private void send(String payload) {
            if (alive.get()) {
                ws.sendText(payload, true);
            }
        }
    }
}
