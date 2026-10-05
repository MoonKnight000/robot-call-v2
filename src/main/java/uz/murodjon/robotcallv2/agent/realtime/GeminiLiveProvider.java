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
import java.util.*;
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
 * An unregistered engine leaves {@code EngineOptionsResponse.realtime} empty, so no company can
 * select a mode this deployment cannot run.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.gemini-live.api-key:}'.isBlank()")
@SuppressWarnings("deprecation")
public class GeminiLiveProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveProvider.class);
    private final ObjectMapper objectMapper;

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
    private static final String DEFAULT_LIVE_MODEL = "gemini-3.8-live-extended-thinking";

    /**
     * Models whose tools are declared {@code NON_BLOCKING}: the engine keeps talking while a
     * tool runs instead of sitting silent until it answers. 3.1 Flash Live cannot do this,
     * and 3.8 Live Extended Thinking rejects anything else — so it is the model, not a
     * setting, that decides.
     */
    private static final String ASYNC_TOOL_MODEL_PREFIX = "gemini-3.8-live";

    private final RealtimeProperties realtimeProperties;
    private volatile HttpClient client;

    public GeminiLiveProvider(RealtimeProperties realtimeProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.realtimeProperties = realtimeProperties;
    }

    @PostConstruct
    public void init() {
        GeminiLiveProperties live = realtimeProperties.geminiLive();
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
     * ({@code RealtimeEchoTool} under src/test), not one this method can answer.
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
    public boolean streamsOutputTranscript() {
        return true;
    }

    @Override
    public String resolveModel(RealtimeCallConfig config) {
        return resolveModelName(config.modelOr(realtimeProperties.geminiLive().model()));
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        GeminiLiveProperties live = realtimeProperties.geminiLive();
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_NOT_INITIALIZED,
                    "gemini-live", "gemini-live");
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
    private String setupMessage(RealtimeCallConfig config, GeminiLiveProperties live,
                                       String resumptionHandle) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode setup = root.putObject("setup");
        String model = resolveModelName(config.modelOr(live.model()));
        setup.put("model", "models/" + model);

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
        if (live.affectiveDialog()) {
            generation.put("enableAffectiveDialog", true);
        }
        if (live.proactiveAudio()) {
            setup.putObject("proactivity").put("proactiveAudio", true);
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

        // The engine's own turn detection, tuned for a phone line rather than a laptop
        // microphone. Only what is configured is sent; the rest stays the engine's default.
        ObjectNode activityDetection = objectMapper.createObjectNode();
        String startSensitivity = sensitivity(live.startOfSpeechSensitivity());
        if (startSensitivity != null) {
            activityDetection.put("startOfSpeechSensitivity", "START_SENSITIVITY_" + startSensitivity);
        }
        String endSensitivity = sensitivity(live.endOfSpeechSensitivity());
        if (endSensitivity != null) {
            activityDetection.put("endOfSpeechSensitivity", "END_SENSITIVITY_" + endSensitivity);
        }
        if (live.prefixPaddingMs() > 0) {
            activityDetection.put("prefixPaddingMs", live.prefixPaddingMs());
        }
        if (live.silenceDurationMs() > 0) {
            activityDetection.put("silenceDurationMs", live.silenceDurationMs());
        }
        if (!activityDetection.isEmpty()) {
            setup.putObject("realtimeInputConfig").set("automaticActivityDetection", activityDetection);
        }
        // Without it a long call — many turns, tool results in between — fills the context
        // and the server ends the session; with it the oldest turns are dropped instead.
        // The instructions (and the facts in them) are never part of what is dropped.
        if (live.contextCompressionTriggerTokens() > 0) {
            ObjectNode compression = setup.putObject("contextWindowCompression");
            compression.put("triggerTokens", live.contextCompressionTriggerTokens());
            compression.putObject("slidingWindow");
        }

        List<ToolCallback> tools = config.tools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode declarations = setup.putArray("tools").addObject().putArray("functionDeclarations");
            boolean asyncTools = supportsAsyncTools(model);
            for (ToolCallback tool : tools) {
                declarations.add(declarationFor(tool, asyncTools));
            }
        }
        return root.toString();
    }

    /** {@code HIGH}/{@code LOW} as configured, or null for blank or anything the API would reject. */
    private static String sensitivity(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String value = configured.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"HIGH".equals(value) && !"LOW".equals(value)) {
            log.warn("Gemini Live speech sensitivity '{}' is neither HIGH nor LOW — using the engine default", configured);
            return null;
        }
        return value;
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

    /** See {@link #ASYNC_TOOL_MODEL_PREFIX}; takes the already resolved model name. */
    private static boolean supportsAsyncTools(String model) {
        return model.startsWith(ASYNC_TOOL_MODEL_PREFIX);
    }

    /**
     * One scenario tool as Gemini declares it. The schema is reused verbatim from the
     * cascade pipeline's {@link ToolCallback} — the same tool, described once.
     */
    private ObjectNode declarationFor(ToolCallback tool, boolean async) {
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.put("name", tool.getToolDefinition().name());
        declaration.put("description", tool.getToolDefinition().description());
        if (async) {
            declaration.put("behavior", "NON_BLOCKING");
        }
        try {
            JsonNode parameters = objectMapper.readTree(tool.getToolDefinition().inputSchema());
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
    private final class ResponseHandler extends JsonWebSocketListener {

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
        private final StringBuilder heard = new StringBuilder();
        private final StringBuilder spoken = new StringBuilder();
        /**
         * Whether a turn is in progress. Usage can arrive in the middle of a turn — and then
         * more than once, each report superseding the last — or after the turn has already
         * settled; held while a turn is open, reported at once otherwise, so it is counted
         * once either way.
         */
        private boolean turnOpen;
        /** The latest usage report of the open turn, not yet passed on. */
        private JsonNode pendingUsage;

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
                if (node.has("usageMetadata")) {
                    captureUsage(node.get("usageMetadata"));
                }
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
                if (node.has("toolCallCancellation")) {
                    // The caller cut in and the engine dropped these calls; the tools are
                    // already running and cannot be stopped, but their answers must not be sent.
                    node.get("toolCallCancellation").path("ids")
                            .forEach(id -> session.cancelToolCall(id.asText("")));
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
                if (!node.has("usageMetadata")) {
                    log.debug("[{}] Gemini Live message ignored: {}", channelId, json);
                }
            } catch (Exception e) {
                log.warn("[{}] Gemini Live sent an unparseable message ({}): {}", channelId, e.getMessage(), json);
            }
        }

        private void handleServerContent(JsonNode content) {
            // 3.8 models keep reasoning and waiting on tools after turnComplete; only this
            // status says whether an answer is still coming. Older models never send it.
            String status = content.path("interactionStatus").asText("");
            if (!status.isEmpty()) {
                listener.onEngineWorking(status.endsWith("IN_PROGRESS"));
            }
            if (content.has("modelTurn") || content.has("inputTranscription") || content.has("outputTranscription")) {
                turnOpen = true;
            }
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
            String said = content.path("outputTranscription").path("text").asText("");
            if (!said.isEmpty()) {
                spoken.append(said);
                listener.onOutputTranscriptDelta(said);
            }

            if (content.path("interrupted").asBoolean(false)) {
                // Playback is stopped first, so that the transcript released below can be
                // trimmed to what the caller actually heard before cutting in.
                listener.onInterrupted();
                settleTurn();
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
            turnOpen = false;
            JsonNode usage = pendingUsage;
            pendingUsage = null;
            if (usage != null) {
                reportUsage(usage);
            }
        }

        private void captureUsage(JsonNode usage) {
            if (turnOpen) {
                pendingUsage = usage;
            } else {
                reportUsage(usage);
            }
        }

        /**
         * One usage report, as the vendor bills it: the prompt includes the conversation so
         * far (and tool-use prompts), the completion includes thinking. Vertex names the
         * completion {@code candidatesTokenCount}, AI Studio {@code responseTokenCount}.
         */
        private void reportUsage(JsonNode usage) {
            long prompt = usage.path("promptTokenCount").asLong(0)
                    + usage.path("toolUsePromptTokenCount").asLong(0);
            long completion = usage.path("responseTokenCount").asLong(usage.path("candidatesTokenCount").asLong(0))
                    + usage.path("thoughtsTokenCount").asLong(0);
            long cached = usage.path("cachedContentTokenCount").asLong(0);
            if (prompt > 0 || completion > 0) {
                listener.onUsage(prompt, completion, cached);
            }
        }

        private void handleToolCall(JsonNode toolCall) {
            List<JsonNode> calls = new ArrayList<>();
            toolCall.path("functionCalls").forEach(calls::add);
            List<String> ids = new ArrayList<>(calls.size());
            for (JsonNode call : calls) {
                ids.add(call.path("id").asText(""));
            }
            // The engine wants one toolResponse carrying every result of the batch, so it
            // has to be told what the batch is before the first tool finishes: the calls
            // run in parallel on the dialog engine's executor and come back in whatever
            // order they happen to finish.
            session.expectToolResults(ids);
            for (JsonNode call : calls) {
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
            CallMdc.with(channelId, () -> {
                if (statusCode != WebSocket.NORMAL_CLOSURE) {
                    log.warn("[{}] Gemini Live closed ({}): {}", channelId, statusCode, reason);
                    session.reconnect(generation, new IllegalStateException("closed " + statusCode + ": " + reason));
                } else {
                    // A normal close is either our own hangup or the server letting a
                    // resumable connection go; the session decides which, since only it knows
                    // whether the call is over.
                    session.reconnect(generation, null);
                }
            });
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            ready.countDown();
            CallMdc.with(channelId, () -> {
                log.warn("[{}] Gemini Live error: {}", channelId, error.getMessage());
                session.reconnect(generation, error);
            });
        }
    }

    /**
     * Pushes audio and tool results into the socket. The JDK client forbids a second send
     * before the previous one has completed, so sends are chained rather than fired in
     * parallel — which also keeps the engine's view of the audio in order.
     */
    private final class GeminiLiveSession implements RealtimeSession {

        private final HttpClient httpClient;
        private final GeminiLiveProperties live;
        private final RealtimeCallConfig config;
        private final RealtimeListener listener;
        /** Tools declared {@code NON_BLOCKING}: each result is sent on its own, as soon as it is ready. */
        private final boolean asyncTools;
        /** Survives a reconnect: a tool call made before the drop is answered after it. */
        private final Map<String, String> pendingToolNames = new ConcurrentHashMap<>();
        /**
         * The tool calls of the batch being answered right now, in the order the engine
         * asked for them, each mapped to its result or to null while it is still running.
         * Guards itself — {@link #sendToolResult} is called from one virtual thread per
         * tool.
         */
        private final Map<String, String> toolBatch = new LinkedHashMap<>();
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
        /** Writes queued on {@link #sendChain} and not yet done — audio is dropped past the bound. */
        private final java.util.concurrent.atomic.AtomicInteger pendingSends = new java.util.concurrent.atomic.AtomicInteger();
        /** About two seconds of 20 ms frames. */
        private static final int MAX_PENDING_AUDIO_SENDS = 100;

        private GeminiLiveSession(HttpClient httpClient, GeminiLiveProperties live,
                                  RealtimeCallConfig config, RealtimeListener listener) {
            this.httpClient = httpClient;
            this.live = live;
            this.config = config;
            this.listener = listener;
            this.asyncTools = supportsAsyncTools(resolveModelName(config.modelOr(live.model())));
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
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(live.connectTimeoutSeconds()))
                    .buildAsync(URI.create(live.url() + "?key=" + live.apiKey()),
                            new ResponseHandler(config.channelId(), listener, connectionAlive, ready,
                                    pendingToolNames, this, nextGeneration))
                    .get(live.connectTimeoutSeconds(), TimeUnit.SECONDS);

            // The setup goes straight to the new socket, and the session moves onto it only
            // once the engine has acknowledged it: swapping first let audio frames from the
            // RTP thread reach the new connection ahead of its setup message on a resume.
            // Nothing may be sent before the acknowledgement, and a call is already ringing
            // (or already talking) while this waits — so it is bounded by the connect timeout.
            socket.sendText(setupMessage(config, live, handle), true)
                    .get(live.connectTimeoutSeconds(), TimeUnit.SECONDS);
            // The handler also releases the latch when the socket closes or fails, so a
            // released latch alone is not an acknowledgement — a dead connection is not a
            // resumed one.
            if (!ready.await(live.connectTimeoutSeconds(), TimeUnit.SECONDS) || !connectionAlive.get()) {
                socket.abort();
                throw new ExternalServiceException(ErrorCode.REALTIME_GEMINI_SETUP_FAILED, "gemini-live",
                        live.connectTimeoutSeconds());
            }
            WebSocket previous = webSocket;
            synchronized (this) {
                webSocket = socket;
                alive = connectionAlive;
                generation = nextGeneration;
                sendChain = CompletableFuture.completedFuture(null);
                pendingSends.set(0);
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
            Thread.ofVirtual().name("gemini-resume-" + config.channelId())
                    .start(() -> CallMdc.with(config.channelId(), () -> {
                        try {
                            connect(resumptionHandle);
                            log.info("[{}] Gemini Live resumed on a new connection", config.channelId());
                            // A status from the dropped socket says nothing about the new one.
                            listener.onEngineWorking(false);
                            listener.onReconnected();
                        } catch (Exception e) {
                            log.warn("[{}] Gemini Live could not be resumed: {}", config.channelId(), e.getMessage());
                            listener.onClosed(cause != null ? cause : e);
                        } finally {
                            reconnecting.set(false);
                        }
                    }));
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (pcm == null || pcm.length == 0) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asShortBuffer().put(pcm);
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode audio = root.putObject("realtimeInput").putObject("audio");
            audio.put("data", Base64.getEncoder().encodeToString(buffer.array()));
            audio.put("mimeType", "audio/pcm;rate=" + INPUT_RATE);
            // A stalled socket must not grow the chain by 50 base64 frames a second: past
            // the bound the frame is dropped — stale audio replayed later is worse than none.
            if (pendingSends.get() >= MAX_PENDING_AUDIO_SENDS) {
                log.debug("[{}] Gemini Live uplink {} frames behind — dropping one", config.channelId(),
                        MAX_PENDING_AUDIO_SENDS);
                return;
            }
            send(root.toString());
        }

        @Override
        public void sendUserText(String text) {
            // Without turnComplete the engine keeps waiting for the rest of the turn and
            // never answers — which on a freshly answered line is heard as dead air.
            send(clientContent(text, true));
        }

        @Override
        public void sendContext(String text) {
            // Here waiting is the point: the text joins the conversation and is answered
            // together with the caller's next turn, not on its own.
            send(clientContent(text, false));
        }

        private String clientContent(String text, boolean turnComplete) {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode content = root.putObject("clientContent");
            content.putArray("turns").addObject()
                    .put("role", "user")
                    .putArray("parts").addObject().put("text", text);
            content.put("turnComplete", turnComplete);
            return root.toString();
        }

        /**
         * Open a fresh batch, holding a slot per tool the engine just asked for.
         *
         * <p>Order is the engine's, not the order the tools happen to finish in.
         */
        private void expectToolResults(List<String> ids) {
            if (asyncTools) {
                // Non-blocking calls are not answered as a batch, and a new call may come
                // while earlier ones are still running — clearing here would lose them.
                return;
            }
            synchronized (toolBatch) {
                toolBatch.clear();
                ids.forEach(id -> toolBatch.put(id, null));
            }
        }

        /**
         * Collects one tool's result and, once the whole batch has landed, answers it in a
         * single message.
         *
         * <p>One message rather than one per tool because that is what the protocol asks
         * for: a batch is answered when every {@code functionResponse} in it is present.
         * Answering them one at a time left the engine holding a half-answered batch — on
         * a recorded call it made two calls in one turn, got two separate replies, and
         * never spoke again; the caller sat in silence and hung up.
         */
        @Override
        public void sendToolResult(String callId, String result) {
            if (asyncTools) {
                sendAsyncToolResult(callId, result == null ? "" : result);
                return;
            }
            String json;
            synchronized (toolBatch) {
                toolBatch.put(callId, result == null ? "" : result);
                if (toolBatch.containsValue(null)) {
                    return;
                }
                json = toolResponseMessage(toolBatch, null);
                toolBatch.clear();
            }
            send(json);
        }

        /**
         * A non-blocking call is answered on its own. {@code WHEN_IDLE}: the engine finishes
         * the sentence it is saying before it uses the result, rather than being cut off
         * mid-word ({@code INTERRUPT}) or keeping the result to itself ({@code SILENT}) —
         * a refusal or an error has to be acted on.
         */
        private void sendAsyncToolResult(String callId, String result) {
            if (!pendingToolNames.containsKey(callId)) {
                log.debug("[{}] tool call {} was cancelled by the engine — result dropped", config.channelId(), callId);
                return;
            }
            send(toolResponseMessage(Map.of(callId, result), "WHEN_IDLE"));
        }

        private void cancelToolCall(String callId) {
            if (asyncTools) {
                pendingToolNames.remove(callId);
            }
        }

        /** @param scheduling when a non-blocking result is used; null for a blocking batch */
        private String toolResponseMessage(Map<String, String> batch, String scheduling) {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode responses = root.putObject("toolResponse").putArray("functionResponses");
            batch.forEach((id, result) -> {
                ObjectNode response = responses.addObject();
                response.put("id", id);
                // The engine matches a response by id but wants the name back with it; the
                // dialog driver only carries the id, so the name is remembered from the call.
                String name = pendingToolNames.remove(id);
                if (name != null) {
                    response.put("name", name);
                }
                ObjectNode body = response.putObject("response");
                body.put("result", result);
                if (scheduling != null) {
                    body.put("scheduling", scheduling);
                }
            });
            return root.toString();
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
            pendingSends.incrementAndGet();
            sendChain = sendChain
                    .thenCompose(ignored -> socket.sendText(json, true))
                    .handle((sent, e) -> {
                        pendingSends.decrementAndGet();
                        if (e != null) {
                            connectionAlive.set(false);
                        }
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
