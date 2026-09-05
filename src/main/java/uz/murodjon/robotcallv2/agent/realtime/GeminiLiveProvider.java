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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gemini Live over the {@code BidiGenerateContent} WebSocket — one vendor doing
 * recognition, reasoning and speech for a whole call (PROJECT.md §2.4/§2.5).
 *
 * <p>The session speaks 16 kHz PCM in and 24 kHz PCM out, so the 8 kHz RTP leg is
 * resampled on both sides ({@code Resampler}). Endpointing and barge-in are the engine's
 * own: it decides when the caller has finished and stops itself when talked over
 * ({@code serverContent.interrupted}), which is why none of the cascade pipeline's VAD
 * gating or endpointing settings apply to a call running here.
 *
 * <p>Registered only when {@code voice-agent.realtime.gemini-live.api-key} is set —
 * which is why the condition is an expression rather than {@code @ConditionalOnProperty}:
 * the key defaults to the empty string, and an empty string counts as "present" there.
 * An unregistered engine leaves {@code EngineOptions.realtime} empty, so no company can
 * select a mode this deployment cannot run.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.gemini-live.api-key:}'.isBlank()")
@SuppressWarnings("deprecation")
public class GeminiLiveProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Rates fixed by the protocol, not config knobs. The API documents 16 kHz
     * little-endian mono s16 for input and says output "always uses a sample rate of
     * 24kHz" — 8 kHz audio labelled as 16 kHz is accepted on the wire and comes back as
     * gibberish.
     */
    private static final int INPUT_RATE = 16000;
    private static final int OUTPUT_RATE = 24000;

    /**
     * Substituted for a model that cannot hold a Live session. It has to be a Live model
     * itself — a plain chat id is accepted by the URL and then never answers the setup,
     * which surfaces as a connect timeout and reads like a network fault.
     */
    private static final String DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview";

    private final RealtimeProperties props;
    private volatile HttpClient client;

    public GeminiLiveProvider(RealtimeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        GeminiLiveProperties live = props.geminiLive();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(live.connectTimeoutSeconds()))
                .build();
        log.info("Gemini Live ready (model={}, voice={}, {} Hz in / {} Hz out)",
                resolveModelName(live.model()), live.voice() == null || live.voice().isBlank() ? "default" : live.voice(),
                INPUT_RATE, OUTPUT_RATE);
    }

    @Override
    public String name() {
        return "gemini-live";
    }

    /**
     * Every language, because the engine picks one itself — native-audio models detect
     * the language from the audio rather than being told. Whether its Uzbek is
     * <em>good</em> is a quality question answered by listening
     * ({@link RealtimeEchoTool}), not one this method can answer.
     */
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
        GeminiLiveProperties live = props.geminiLive();
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED,
                    "gemini-live", "not initialized");
        }
        GeminiLiveSession session = new GeminiLiveSession(current, live, config, listener);
        try {
            session.connect(null);
            log.info("[{}] Gemini Live session open (lang={}, voice={}, tools={})",
                    config.channelId(), config.language(), resolveVoice(config, live),
                    config.tools() == null ? 0 : config.tools().size());
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED,
                    "gemini-live", e, e.getMessage());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_CONNECT_FAILED,
                    "gemini-live", e, e.getMessage());
        }
    }

    /**
     * The voice this call speaks with: what the campaign chose for the call's language,
     * falling back to the deployment-wide setting. Blank leaves the model on its default.
     */
    private static String resolveVoice(RealtimeCallConfig config, GeminiLiveProperties live) {
        if (config.voice() != null && !config.voice().isBlank()) {
            return config.voice();
        }
        return live.voice();
    }

    /**
     * The one message that configures the whole conversation. A realtime engine is
     * instructed at connect time, not per turn, so everything the cascade pipeline
     * rebuilds on every turn — instructions, tools, language — is settled here.
     */
    private static String setupMessage(RealtimeCallConfig config, GeminiLiveProperties live,
                                       String resumptionHandle) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode setup = root.putObject("setup");
        setup.put("model", "models/" + resolveModelName(live.model()));

        // A Gemini Live connection lives about ten minutes and is then closed by the
        // server, whatever the call is doing. Asking for session resumption makes the
        // conversation outlive the socket: the engine hands back a handle, and a
        // reconnect that presents it continues the same session rather than meeting a
        // caller mid-sentence with a stranger. An empty object opens a fresh session.
        ObjectNode resumption = setup.putObject("sessionResumption");
        if (resumptionHandle != null && !resumptionHandle.isBlank()) {
            resumption.put("handle", resumptionHandle);
        }

        ObjectNode generation = setup.putObject("generationConfig");
        generation.putArray("responseModalities").add("AUDIO");
        ObjectNode speech = generation.putObject("speechConfig");
        String voice = resolveVoice(config, live);
        if (voice != null && !voice.isBlank()) {
            speech.putObject("voiceConfig").putObject("prebuiltVoiceConfig").put("voiceName", voice);
        }
        if (config.language() != null && !config.language().isBlank()) {
            speech.put("languageCode", config.language());
        }

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            setup.putObject("systemInstruction").putArray("parts")
                    .addObject().put("text", config.systemPrompt());
        }
        // Both transcriptions on: the input one is what lands in call_transcript, and the
        // output one is the only text of the bot's own words a realtime call ever sees
        // (§4.4 — an audit after the fact, not a gate before speaking).
        setup.putObject("inputAudioTranscription");
        setup.putObject("outputAudioTranscription");

        List<ToolCallback> tools = config.tools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode declarations = setup.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolCallback tool : tools) {
                declarations.add(declarationFor(tool));
            }
        }
        return root.toString();
    }

    private static String resolveModelName(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LIVE_MODEL;
        }
        String trimmed = configured.trim();
        if (trimmed.startsWith("models/")) {
            trimmed = trimmed.substring("models/".length());
        }
        if (trimmed.contains("gemini-2") || trimmed.contains("gemini-1")) {
            log.warn("Live model '{}' predates the Live API — falling back to {}", trimmed, DEFAULT_LIVE_MODEL);
            return DEFAULT_LIVE_MODEL;
        }
        return trimmed;
    }

    /**
     * One scenario tool as Gemini declares it. The schema is reused verbatim from the
     * cascade pipeline's {@link ToolCallback} — the same tool, described once.
     */
    private static ObjectNode declarationFor(ToolCallback tool) {
        ObjectNode declaration = MAPPER.createObjectNode();
        declaration.put("name", tool.getToolDefinition().name());
        declaration.put("description", tool.getToolDefinition().description());
        try {
            JsonNode parameters = MAPPER.readTree(tool.getToolDefinition().inputSchema());
            stripUnsupportedSchemaKeywords(parameters);
            declaration.set("parameters", parameters);
        } catch (Exception e) {
            // A tool whose schema will not parse is declared without parameters rather
            // than failing the whole session: losing one tool's arguments beats losing
            // the call.
            log.warn("Tool '{}' has an unparseable input schema, declaring it without parameters: {}",
                    tool.getToolDefinition().name(), e.getMessage());
        }
        return declaration;
    }

    /**
     * Gemini Live's setup message is validated against a restricted OpenAPI-style schema
     * subset: standard JSON-Schema keywords like {@code $schema} and {@code additionalProperties}
     * (which Spring AI's generator stamps onto every object node, including nested ones) are
     * "unknown name" and close the whole session with a 1007 before the caller says a word.
     */
    private static void stripUnsupportedSchemaKeywords(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove("$schema");
            objectNode.remove("additionalProperties");
            objectNode.elements().forEachRemaining(GeminiLiveProvider::stripUnsupportedSchemaKeywords);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.elements().forEachRemaining(GeminiLiveProvider::stripUnsupportedSchemaKeywords);
        }
    }

    /**
     * Maps the engine's messages onto the listener.
     *
     * <p>Transcriptions arrive as fragments, not sentences. They are accumulated and
     * released when the turn settles — on {@code turnComplete}, or on {@code interrupted},
     * where what has accumulated is exactly what the caller heard before cutting in.
     * Nothing downstream wants a guardrail audit run on half a word.
     */
    private static final class ResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final Map<String, String> pendingToolNames;
        private final GeminiLiveSession session;
        /**
         * Which connection of the session this handler belongs to. A replaced socket still
         * reports its own close afterwards, and without this that late event would be read
         * as the live connection dropping and start a second reconnect.
         */
        private final int generation;
        /** A message is only parseable once {@code last} is set; frames may split it. */
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder heard = new StringBuilder();
        private final StringBuilder spoken = new StringBuilder();

        private ResponseHandler(String channelId, RealtimeListener listener, AtomicBoolean alive,
                                CountDownLatch ready, Map<String, String> pendingToolNames,
                                GeminiLiveSession session, int generation) {
            this.channelId = channelId;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
            this.pendingToolNames = pendingToolNames;
            this.session = session;
            this.generation = generation;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String json = message.toString();
                message.setLength(0);
                handle(json);
            }
            // Overriding onText replaces the default implementation's own request(1) —
            // without this the stream delivers one message and then goes silent.
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // The endpoint sends its JSON as binary frames as readily as text ones, so
            // both paths feed the same parser rather than one of them silently dropping
            // every message the session depends on.
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
                if (node.has("setupComplete")) {
                    ready.countDown();
                    return;
                }
                if (node.has("serverContent")) {
                    handleServerContent(node.get("serverContent"));
                    return;
                }
                if (node.has("toolCall")) {
                    handleToolCall(node.get("toolCall"));
                    return;
                }
                if (node.has("sessionResumptionUpdate")) {
                    JsonNode update = node.get("sessionResumptionUpdate");
                    if (update.path("resumable").asBoolean(false)) {
                        session.rememberHandle(update.path("newHandle").asText(null));
                    }
                    return;
                }
                if (node.has("goAway")) {
                    // The connection's time is up (about ten minutes). This arrives BEFORE
                    // the close, which is the whole point of acting on it: reconnecting now
                    // swaps the socket under a conversation that is still running, instead
                    // of noticing the drop once the caller is already talking to nothing.
                    log.info("[{}] Gemini Live is closing the connection ({}) — reconnecting",
                            channelId, node.get("goAway"));
                    session.reconnect(generation, null);
                    return;
                }
                log.debug("[{}] Gemini Live message ignored: {}", channelId, json);
            } catch (Exception e) {
                log.warn("[{}] Gemini Live sent an unparseable message ({}): {}", channelId, e.getMessage(), json);
            }
        }

        private void handleServerContent(JsonNode content) {
            for (JsonNode part : content.path("modelTurn").path("parts")) {
                JsonNode inline = part.path("inlineData");
                if (!inline.isMissingNode() && inline.hasNonNull("data")) {
                    listener.onBotAudio(toPcm(inline.get("data").asText()));
                }
            }
            String input = content.path("inputTranscription").path("text").asText("");
            if (!input.isEmpty()) {
                heard.append(input);
                listener.onInputTranscript(input, false);
            }
            spoken.append(content.path("outputTranscription").path("text").asText(""));

            if (content.path("interrupted").asBoolean(false)) {
                settleTurn();
                listener.onInterrupted();
                return;
            }
            if (content.path("turnComplete").asBoolean(false)) {
                settleTurn();
                listener.onTurnComplete();
            }
        }

        /** Release both accumulated transcripts and start the next turn empty. */
        private void settleTurn() {
            if (heard.length() > 0) {
                listener.onInputTranscript(heard.toString(), true);
                heard.setLength(0);
            }
            if (spoken.length() > 0) {
                listener.onOutputTranscript(spoken.toString());
                spoken.setLength(0);
            }
        }

        private void handleToolCall(JsonNode toolCall) {
            for (JsonNode call : toolCall.path("functionCalls")) {
                String id = call.path("id").asText("");
                String name = call.path("name").asText("");
                pendingToolNames.put(id, name);
                listener.onToolCall(id, name, call.path("args").toString());
            }
        }

        /** Base64 → 24 kHz little-endian mono s16 samples. */
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
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                log.warn("[{}] Gemini Live closed ({}): {}", channelId, statusCode, reason);
                session.reconnect(generation, new IllegalStateException("closed " + statusCode + ": " + reason));
            } else {
                // A normal close is either our own hangup or the server letting a
                // resumable connection go; the session decides which, since only it knows
                // whether the call is over.
                session.reconnect(generation, null);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            ready.countDown();
            log.warn("[{}] Gemini Live error: {}", channelId, error.getMessage());
            session.reconnect(generation, error);
        }
    }

    /**
     * Pushes audio and tool results into the socket. The JDK client forbids a second send
     * before the previous one has completed, so sends are chained rather than fired in
     * parallel — which also keeps the engine's view of the audio in order.
     */
    private final class GeminiLiveSession implements RealtimeSession {

        private final HttpClient http;
        private final GeminiLiveProperties live;
        private final RealtimeCallConfig config;
        private final RealtimeListener listener;
        /** Survives a reconnect: a tool call made before the drop is answered after it. */
        private final Map<String, String> pendingToolNames = new ConcurrentHashMap<>();
        /** Guards against a second reconnect starting while one is still in flight. */
        private final AtomicBoolean reconnecting = new AtomicBoolean();

        private volatile WebSocket webSocket;
        /** Liveness of the <em>current</em> connection; replaced wholesale on reconnect. */
        private volatile AtomicBoolean alive = new AtomicBoolean(false);
        /** Latest resumption handle the engine issued, or null while it has issued none. */
        private volatile String resumptionHandle;
        private volatile int generation;
        private volatile boolean closed;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

        private GeminiLiveSession(HttpClient http, GeminiLiveProperties live,
                                  RealtimeCallConfig config, RealtimeListener listener) {
            this.http = http;
            this.live = live;
            this.config = config;
            this.listener = listener;
        }

        /**
         * Open a connection and put this session on it, replacing whatever it was on
         * before. Passing a handle continues an existing conversation; null starts one.
         *
         * <p>The new socket is only swapped in once the engine has acknowledged the setup,
         * so a failed reconnect leaves the old connection to be reported as the drop it is
         * rather than silently swallowing the call.
         */
        private void connect(String handle) throws Exception {
            AtomicBoolean connectionAlive = new AtomicBoolean(true);
            CountDownLatch ready = new CountDownLatch(1);
            int nextGeneration = generation + 1;
            WebSocket socket = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(live.connectTimeoutSeconds()))
                    .buildAsync(URI.create(live.url() + "?key=" + live.apiKey()),
                            new ResponseHandler(config.channelId(), listener, connectionAlive, ready,
                                    pendingToolNames, this, nextGeneration))
                    .get(live.connectTimeoutSeconds(), TimeUnit.SECONDS);

            WebSocket previous = webSocket;
            synchronized (this) {
                webSocket = socket;
                alive = connectionAlive;
                generation = nextGeneration;
                sendChain = CompletableFuture.completedFuture(null);
            }
            send(setupMessage(config, live, handle));
            // Nothing may be sent before the engine acknowledges the setup, and a call is
            // already ringing (or already talking) while this waits — so it is bounded by
            // the same timeout as the connect rather than left open.
            if (!ready.await(live.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                socket.abort();
                throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_SETUP_FAILED, "gemini-live",
                        live.connectTimeoutSeconds());
            }
            if (previous != null) {
                previous.abort();
            }
        }

        private void rememberHandle(String handle) {
            if (handle != null && !handle.isBlank()) {
                resumptionHandle = handle;
            }
        }

        /**
         * React to a connection ending — either announced by {@code goAway} or simply
         * gone. With a resumption handle in hand the conversation moves to a fresh
         * connection and the caller hears nothing; without one, or if the reconnect
         * itself fails, the drop is reported and the call is the dialog engine's problem.
         *
         * @param fromGeneration which connection is reporting; a superseded one is ignored
         * @param cause          what ended it, or null for a close with no error
         */
        private void reconnect(int fromGeneration, Throwable cause) {
            if (closed || fromGeneration != generation) {
                return;
            }
            if (resumptionHandle == null) {
                listener.onClosed(cause);
                return;
            }
            if (!reconnecting.compareAndSet(false, true)) {
                return;
            }
            // Off the WebSocket's own callback thread: connect() blocks on the handshake
            // and the setup acknowledgement, neither of which may run on it.
            Thread.ofVirtual().name("gemini-resume-" + config.channelId()).start(() -> {
                try {
                    connect(resumptionHandle);
                    log.info("[{}] Gemini Live resumed on a new connection", config.channelId());
                } catch (Exception e) {
                    log.warn("[{}] Gemini Live could not be resumed: {}", config.channelId(), e.getMessage());
                    listener.onClosed(cause != null ? cause : e);
                } finally {
                    reconnecting.set(false);
                }
            });
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (pcm == null || pcm.length == 0) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asShortBuffer().put(pcm);
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode audio = root.putObject("realtimeInput").putObject("audio");
            audio.put("data", Base64.getEncoder().encodeToString(buffer.array()));
            audio.put("mimeType", "audio/pcm;rate=" + INPUT_RATE);
            send(root.toString());
        }

        @Override
        public void sendUserText(String text) {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode content = root.putObject("clientContent");
            content.putArray("turns").addObject()
                    .put("role", "user")
                    .putArray("parts").addObject().put("text", text);
            // Without this the engine keeps waiting for the rest of the turn and never
            // answers — which on a freshly answered line is heard as dead air.
            content.put("turnComplete", true);
            send(root.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            // The engine matches a response by id but wants the name back with it; the
            // dialog driver only carries the id, so the name is remembered from the call.
            String name = pendingToolNames.remove(callId);
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode response = root.putObject("toolResponse").putArray("functionResponses").addObject();
            response.put("id", callId);
            if (name != null) {
                response.put("name", name);
            }
            response.putObject("response").put("result", result);
            send(root.toString());
        }

        private synchronized void send(String json) {
            WebSocket socket = webSocket;
            AtomicBoolean connectionAlive = alive;
            if (closed || socket == null || !connectionAlive.get()) {
                // Between a drop and the resumed connection there is a short gap. Audio
                // that lands in it is dropped rather than queued: by the time the socket
                // is back those frames are stale, and replaying them would have the engine
                // answer speech the caller has already moved on from.
                return;
            }
            sendChain = sendChain
                    .thenCompose(ignored -> socket.sendText(json, true))
                    .thenAccept(sent -> {
                    })
                    .exceptionally(e -> {
                        connectionAlive.set(false);
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
            WebSocket socket = webSocket;
            if (socket == null) {
                return;
            }
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended")
                    .exceptionally(e -> {
                        socket.abort();
                        return null;
                    });
        }
    }
}
