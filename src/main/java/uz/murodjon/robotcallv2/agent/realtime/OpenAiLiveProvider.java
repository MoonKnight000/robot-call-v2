package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.codec.G711Codec;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.agent.provider.OpenAiChatOptionsStrategy;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI GPT-Live ({@code gpt-live-1}) over WebSocket — {@code wss://api.openai.com/v1/live/sessions}.
 *
 * <p>Not the Realtime API with another model id: a different endpoint and event set. The
 * voice model is full duplex — it listens while it speaks, backchannels, and decides on its
 * own when to talk; there is no turn-detection setting. It does not call tools itself: it
 * delegates to a Responses model ({@code delegation.type = responses}) that holds the tool
 * list, and that model's function calls arrive wrapped in {@code response.event}. Their
 * results go back with {@code response.item.create} and a {@code response.create} once every
 * call of the response is answered.
 *
 * <p>Two things the call pipeline expects and the engine does not send, made up here:
 * <ul>
 *   <li><b>Turn boundaries.</b> There is no turn-done or interrupted event. The bot's turn is
 *       taken as over when the caller's transcript starts after the bot spoke, and the
 *       caller's line as final when the bot starts speaking after it.</li>
 *   <li><b>Audio before the first word.</b> The pipeline forwards the caller only once the
 *       engine has spoken, but a full-duplex model runs on the input clock — fed nothing, it
 *       may never greet. Silence is streamed until the first real caller frame.</li>
 * </ul>
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.open-ai-live.api-key:}'.isBlank()")
public class OpenAiLiveProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveProvider.class);

    private static final String NAME = "openai-live";
    private static final int TELEPHONE_RATE = 8000;
    private static final int PCM_RATE = 16000;
    /** Frame length of the silence streamed before the caller is forwarded. */
    private static final int SILENCE_FRAME_MS = 20;
    /**
     * GPT-Live has no turn-detection or interruption settings: its prompting guide controls them
     * through these headed sections instead. Tools live with the delegation model only, so the
     * voice model also has to know to hand the work over — {@code <|SpawnThinking|>} is the token
     * the guide names for that.
     */
    private static final String LIVE_POLICIES = """


            # Backchannel policy
            Rare. This is a phone call: an occasional short acknowledgement while the caller pauses \
            mid-thought is fine, never over the caller's words.

            # Interruption policy
            When the caller starts speaking over you, stop at once and listen. Do not finish the \
            sentence you were saying; answer what the caller said instead.

            # Patience
            Let the caller finish. A short pause in the middle of a sentence, a number or an address \
            is not the end of their turn.

            # Delegation policy
            Generate <|SpawnThinking|> whenever the conversation needs an action or a fact you do \
            not have: ending the call, transferring it, looking something up, or recording a result. \
            Never say an action is done before its result arrives.""";

    private final RealtimeProperties realtimeProperties;
    private final ObjectMapper objectMapper;
    /** The deployment's OpenAI effort (spring.ai.openai.chat.reasoning-effort) for an agent that sets none. */
    private final ReasoningEffort defaultEffort;
    private volatile HttpClient client;

    public OpenAiLiveProvider(RealtimeProperties realtimeProperties, ObjectMapper objectMapper,
                              @Value("${spring.ai.openai.chat.reasoning-effort:low}") String defaultEffort) {
        this.realtimeProperties = realtimeProperties;
        this.objectMapper = objectMapper;
        this.defaultEffort = ReasoningEffort.parse(defaultEffort);
    }

    @PostConstruct
    public void init() {
        OpenAiLiveProperties live = realtimeProperties.openAiLive();
        if (live == null || live.apiKey() == null || live.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(live.connectTimeoutSeconds()))
                .build();
        log.info("OpenAI Live ready (model={}, voice={}, audio={}, delegation={})",
                live.model(), live.voice(), live.audioFormat(), live.delegationModel());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(String language) {
        return true;
    }

    /** Text sent to GPT-Live is commentary it speaks, never a caller turn: no HALF_CASCADE. */
    @Override
    public boolean lacksCallerText() {
        return true;
    }

    @Override
    public int inputSampleRate() {
        return audioRate();
    }

    @Override
    public int outputSampleRate() {
        return audioRate();
    }

    private int audioRate() {
        OpenAiLiveProperties live = realtimeProperties.openAiLive();
        return live != null && live.telephoneAudio() ? TELEPHONE_RATE : PCM_RATE;
    }

    @Override
    public String resolveModel(RealtimeCallConfig config) {
        OpenAiLiveProperties live = realtimeProperties.openAiLive();
        return config.modelOr(live == null ? null : live.model());
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        OpenAiLiveProperties live = realtimeProperties.openAiLive();
        if (current == null || live == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_NOT_INITIALIZED, NAME, NAME);
        }
        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);
        boolean telephone = live.telephoneAudio();
        ToolBatch toolBatch = new ToolBatch();

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + live.apiKey())
                    .connectTimeout(Duration.ofSeconds(live.connectTimeoutSeconds()))
                    .buildAsync(URI.create(live.url()),
                            new LiveEventHandler(config.channelId(), listener, alive, ready, telephone, toolBatch,
                                    live.delegationModel()))
                    .get(live.connectTimeoutSeconds(), TimeUnit.SECONDS);

            LiveSession session = new LiveSession(webSocket, alive, telephone, toolBatch);
            session.send(sessionStartMessage(config, live));

            // Released by session.started, or by the socket closing — the latter is no start.
            if (!ready.await(live.connectTimeoutSeconds(), TimeUnit.SECONDS) || !alive.get()) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_SETUP_FAILED,
                        NAME, NAME, live.connectTimeoutSeconds());
            }
            session.startSilence(config.channelId());

            log.info("[{}] OpenAI Live session open (model={}, voice={}, audio={}, delegation={})",
                    config.channelId(), config.modelOr(live.model()), voiceOf(config, live),
                    live.audioFormat(), live.delegationModel());
            return session;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_CONNECT_FAILED, NAME, e, NAME,
                    e.getMessage());
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_CONNECT_FAILED, NAME, e, NAME,
                    e.getMessage());
        }
    }

    private static String voiceOf(RealtimeCallConfig config, OpenAiLiveProperties live) {
        return config.voice() != null && !config.voice().isBlank() ? config.voice() : live.voice();
    }

    private String sessionStartMessage(RealtimeCallConfig config, OpenAiLiveProperties live) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "session.start");
        ObjectNode session = root.putObject("session");
        session.put("model", config.modelOr(live.model()));
        String prompt = config.systemPrompt() == null ? "" : config.systemPrompt();
        session.put("instructions", prompt + LIVE_POLICIES);

        ObjectNode audio = session.putObject("audio");
        ObjectNode format = audio.putObject("format");
        if (live.telephoneAudio()) {
            format.put("type", "audio/pcmu");
            format.put("rate", TELEPHONE_RATE);
        } else {
            format.put("type", "audio/pcm");
            format.put("rate", PCM_RATE);
        }
        audio.putObject("output").put("voice", voiceOf(config, live));

        ObjectNode delegation = session.putObject("delegation");
        delegation.put("type", "responses");
        ObjectNode responses = delegation.putObject("responses");
        responses.put("model", live.delegationModel());
        // The delegation model decides the tool arguments, so it needs the same rules the
        // voice model speaks under.
        if (!prompt.isBlank()) {
            responses.put("instructions", prompt);
        }
        RealtimeFunctionTools.putFunctionTools(objectMapper, responses, config.tools());
        if (responses.has("tools")) {
            responses.put("tool_choice", "auto");
        }
        // The agent's own level; the deployment's OpenAI default for an agent that sets none.
        ReasoningEffort effort = config.reasoningEffort() != null ? config.reasoningEffort() : defaultEffort;
        if (effort != null) {
            responses.putObject("reasoning")
                    .put("effort", OpenAiChatOptionsStrategy.findReasoningEffort(effort, live.delegationModel()));
        }
        return root.toString();
    }

    private final class LiveEventHandler extends JsonWebSocketListener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final boolean telephone;
        private final ToolBatch toolBatch;
        /** What delegated usage is filed under when the response does not name its model. */
        private final String delegationModel;
        // Only touched on the socket's own thread, one message at a time.
        private final StringBuilder botTranscript = new StringBuilder();
        private final StringBuilder callerTranscript = new StringBuilder();
        private boolean botSpeaking;

        private LiveEventHandler(String channelId, RealtimeListener listener, AtomicBoolean alive,
                                 CountDownLatch ready, boolean telephone, ToolBatch toolBatch,
                                 String delegationModel) {
            this.channelId = channelId;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
            this.telephone = telephone;
            this.toolBatch = toolBatch;
            this.delegationModel = delegationModel;
        }

        @Override
        protected void onMessage(String json) {
            CallMdc.with(channelId, () -> dispatch(json));
        }

        private void dispatch(String json) {
            try {
                JsonNode node = objectMapper.readTree(json);
                String type = node.path("type").asText("");
                switch (type) {
                    case "session.started" -> ready.countDown();
                    case "session.output_audio.delta" -> {
                        botStarts();
                        String delta = node.path("delta").asText("");
                        if (!delta.isEmpty()) {
                            listener.onBotAudio(toPcm(delta));
                        }
                    }
                    case "session.output_transcript.delta" -> {
                        botStarts();
                        botTranscript.append(node.path("delta").asText(""));
                    }
                    case "session.input_transcript.delta" -> {
                        String delta = node.path("delta").asText("");
                        if (delta.isBlank() && callerTranscript.isEmpty()) {
                            return;
                        }
                        finishBotTurn();
                        callerTranscript.append(delta);
                        listener.onInputTranscript(callerTranscript.toString().trim(), false);
                    }
                    case "session.delegation.created" -> listener.onEngineWorking(true);
                    case "response.event" -> onResponseEvent(node.path("event"));
                    case "session.usage.updated" ->
                            log.debug("[{}] OpenAI Live usage: {}s", channelId, node.path("usage").path("seconds").asLong());
                    case "session.closed" -> {
                        finishBotTurn();
                        log.info("[{}] OpenAI Live session closed (reason={}, seconds={})", channelId,
                                node.path("reason").asText(""), node.path("usage").path("seconds").asLong());
                    }
                    case "error" -> log.warn("[{}] OpenAI Live error {}: {}", channelId,
                            node.path("error").path("code").asText(""), node.path("error").path("message").asText(""));
                    default -> log.trace("[{}] OpenAI Live event: {}", channelId, type);
                }
            } catch (Exception e) {
                log.warn("[{}] Unparseable OpenAI Live event: {}", channelId, e.getMessage());
            }
        }

        /** A Responses streaming event of the delegation model, unwrapped. */
        private void onResponseEvent(JsonNode event) {
            String type = event.path("type").asText("");
            switch (type) {
                case "response.output_item.done" -> {
                    JsonNode item = event.path("item");
                    if (!"function_call".equals(item.path("type").asText(""))) {
                        return;
                    }
                    String callId = item.path("call_id").asText("");
                    toolBatch.called(callId);
                    listener.onToolCall(callId, item.path("name").asText(""), item.path("arguments").asText("{}"));
                }
                case "response.completed", "response.failed", "response.incomplete" -> {
                    JsonNode usage = event.path("response").path("usage");
                    if (usage.isObject()) {
                        String model = event.path("response").path("model").asText("");
                        listener.onDelegatedUsage(model.isBlank() ? delegationModel : model,
                                usage.path("input_tokens").asLong(0),
                                usage.path("output_tokens").asLong(0),
                                usage.path("input_tokens_details").path("cached_tokens").asLong(0));
                    }
                    if (toolBatch.responseDone()) {
                        toolBatch.requestResponse.run(); // every result was in before the response ended
                    } else if (!toolBatch.waiting()) {
                        listener.onEngineWorking(false);
                    }
                }
                default -> log.trace("[{}] OpenAI Live delegation event: {}", channelId, type);
            }
        }

        /** The bot started (or keeps) speaking: the caller's pending line is settled. */
        private void botStarts() {
            if (!callerTranscript.isEmpty()) {
                String line = callerTranscript.toString().trim();
                callerTranscript.setLength(0);
                if (!line.isEmpty()) {
                    listener.onInputTranscript(line, true);
                }
            }
            botSpeaking = true;
        }

        /** The caller spoke after the bot: the bot's turn is over. */
        private void finishBotTurn() {
            if (!botSpeaking) {
                return;
            }
            botSpeaking = false;
            String said = botTranscript.toString().trim();
            botTranscript.setLength(0);
            if (!said.isEmpty()) {
                listener.onOutputTranscript(said);
            }
            listener.onTurnComplete();
        }

        /** Base64 audio as 16-bit PCM: decoded from µ-law on a phone line, read as-is otherwise. */
        private short[] toPcm(String base64) {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (telephone) {
                short[] pcm = new short[bytes.length];
                G711Codec.ulawToPcm(bytes, bytes.length, pcm);
                return pcm;
            }
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

    /**
     * The function calls of the delegation response in progress. GPT-Live wants an output
     * for every pending call before {@code response.create}; one goes out once the response
     * has ended and every call has its result — whichever comes last.
     */
    private static final class ToolBatch {

        private final Set<String> outstanding = new HashSet<>();
        private boolean called;
        private boolean responseEnded;
        /** Set by the session once it exists; the handler is built before it. */
        private volatile Runnable requestResponse = () -> {
        };

        synchronized void called(String callId) {
            outstanding.add(callId);
            called = true;
        }

        /** The response ended. True when it made calls and all of them are answered already. */
        synchronized boolean responseDone() {
            if (!called) {
                return false;
            }
            responseEnded = true;
            return settleIfComplete();
        }

        /** One result is in. True when it was the last one of a response that has ended. */
        synchronized boolean answered(String callId) {
            outstanding.remove(callId);
            return responseEnded && settleIfComplete();
        }

        /** Whether results are still owed for the current response. */
        synchronized boolean waiting() {
            return called;
        }

        private boolean settleIfComplete() {
            if (!outstanding.isEmpty()) {
                return false;
            }
            called = false;
            responseEnded = false;
            return true;
        }
    }

    private final class LiveSession implements RealtimeSession {

        /** About two seconds of 20 ms frames. */
        private static final int MAX_PENDING_AUDIO_SENDS = 100;

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final boolean telephone;
        private final ToolBatch toolBatch;
        private final AtomicInteger pendingSends = new AtomicInteger();
        /** Set by the first real caller frame; stops the silence stream. */
        private final AtomicBoolean callerFlowing = new AtomicBoolean();
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        /** Volatile: the silence thread reads it outside the lock. */
        private volatile boolean closed;

        private LiveSession(WebSocket webSocket, AtomicBoolean alive, boolean telephone, ToolBatch toolBatch) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.telephone = telephone;
            this.toolBatch = toolBatch;
            toolBatch.requestResponse = this::requestResponse;
        }

        /** Streams silence on a virtual thread until the caller's own audio takes over. */
        private void startSilence(String channelId) {
            int samples = audioRate() * SILENCE_FRAME_MS / 1000;
            short[] silence = new short[samples];
            Thread.ofVirtual().name("openai-live-silence-" + channelId).start(() -> {
                long next = System.nanoTime();
                while (!callerFlowing.get() && alive.get() && !closed) {
                    appendAudio(silence);
                    next += TimeUnit.MILLISECONDS.toNanos(SILENCE_FRAME_MS);
                    long waitNanos = next - System.nanoTime();
                    if (waitNanos > 0) {
                        try {
                            TimeUnit.NANOSECONDS.sleep(waitNanos);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (pcm == null || pcm.length == 0) {
                return;
            }
            callerFlowing.set(true);
            appendAudio(pcm);
        }

        private void appendAudio(short[] pcm) {
            if (closed || !alive.get() || pendingSends.get() >= MAX_PENDING_AUDIO_SENDS) {
                return; // a stalled socket: stale audio replayed later is worse than none
            }
            byte[] bytes;
            if (telephone) {
                bytes = new byte[pcm.length];
                G711Codec.pcmToUlaw(pcm, pcm.length, bytes);
            } else {
                ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                buffer.asShortBuffer().put(pcm);
                bytes = buffer.array();
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "session.input_audio.append");
            root.put("audio", Base64.getEncoder().encodeToString(bytes));
            send(root.toString());
        }

        /**
         * Every caller of this is a system instruction that wants a spoken answer (the
         * greeting, a goodbye, a "are you there?"). Commentary is what GPT-Live speaks —
         * paraphrased, not read out.
         */
        @Override
        public void sendUserText(String text) {
            append("session.commentary.append", text);
        }

        /** Quiet context: shapes the next answer without one of its own. */
        @Override
        public void sendContext(String text) {
            append("session.thinking.append", text);
        }

        private void append(String type, String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", type);
            root.putNull("delegation_id"); // session-wide, not tied to one delegation
            root.put("content", text);
            send(root.toString());
        }

        @Override
        public void sendToolResult(String callId, String result) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "response.item.create");
            ObjectNode item = root.putObject("item");
            item.put("type", "function_call_output");
            item.put("call_id", callId);
            item.put("output", result != null ? result : "{}");
            send(root.toString());
            if (toolBatch.answered(callId)) {
                requestResponse();
            }
        }

        /** One response.create per tool batch — see {@link ToolBatch}. */
        private void requestResponse() {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "response.create");
            send(root.toString());
        }

        private synchronized void send(String json) {
            if (closed || !alive.get()) {
                return;
            }
            pendingSends.incrementAndGet();
            sendChain = sendChain
                    .thenCompose(ignored -> webSocket.sendText(json, true))
                    .handle((sent, e) -> {
                        pendingSends.decrementAndGet();
                        if (e != null) {
                            alive.set(false);
                        }
                        return null;
                    });
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (alive.get()) {
                send(objectMapper.createObjectNode().put("type", "session.close").toString());
            }
            closed = true;
            alive.set(false);
            sendChain.thenCompose(ignored -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended"))
                    .exceptionally(e -> {
                        webSocket.abort();
                        return null;
                    });
        }
    }
}
