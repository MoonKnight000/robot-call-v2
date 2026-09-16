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
import uz.murodjon.robotcallv2.agent.codec.G711Codec;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI Realtime API Provider over WebSocket (GPT-4o Realtime / GPT-4o-mini Realtime).
 *
 * <p>Delivers true full-duplex Speech-to-Speech (S2S) multimodal streaming with
 * integrated server-side VAD, function calling, and sub-second conversational turnaround.
 *
 * <p>On a phone line the session speaks G.711 µ-law at 8 kHz both ways
 * ({@code audio-format: g711_ulaw}): the same audio the RTP leg carries, with no
 * resampling on either side. Turn detection is semantic by default — the engine judges
 * from what was said whether the caller has finished, not only from a silence — and the
 * caller's side is cleaned with far-field noise reduction before the engine hears it.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.realtime.open-ai.api-key:}'.isBlank()")
@SuppressWarnings("deprecation")
public class OpenAiRealtimeProvider implements RealtimeProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int TELEPHONE_RATE = 8000;
    /** Mean token probability below which a caller transcript is flagged as barely heard. */
    private static final double LOW_CONFIDENCE = 0.5;
    private static final int PCM_RATE = 24000;

    private final RealtimeProperties realtimeProperties;
    private volatile HttpClient client;

    public OpenAiRealtimeProvider(RealtimeProperties realtimeProperties) {
        this.realtimeProperties = realtimeProperties;
    }

    @PostConstruct
    public void init() {
        OpenAiRealtimeProperties openAi = realtimeProperties.openAi();
        if (openAi == null || openAi.apiKey() == null || openAi.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(openAi.connectTimeoutSeconds()))
                .build();
        log.info("OpenAI Realtime ready (model={}, voice={}, audio={}, turn detection={})",
                openAi.model(), openAi.voice(), openAi.audioFormat(), openAi.turnDetection());
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
        return audioRate();
    }

    @Override
    public int outputSampleRate() {
        return audioRate();
    }

    private int audioRate() {
        OpenAiRealtimeProperties openAi = realtimeProperties.openAi();
        return openAi != null && openAi.telephoneAudio() ? TELEPHONE_RATE : PCM_RATE;
    }

    @Override
    public String resolveModel(RealtimeCallConfig config) {
        OpenAiRealtimeProperties openAi = realtimeProperties.openAi();
        return config.modelOr(openAi == null ? null : openAi.model());
    }

    @Override
    public RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener) {
        HttpClient current = client;
        OpenAiRealtimeProperties openAi = realtimeProperties.openAi();
        if (current == null || openAi == null) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_NOT_INITIALIZED,
                    "openai-realtime", "openai-realtime");
        }
        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(1);
        boolean telephone = openAi.telephoneAudio();
        // The assistant audio item being played right now — what a barge-in truncates.
        AtomicReference<String> speakingItem = new AtomicReference<>();
        ToolBatch toolBatch = new ToolBatch();

        String wsUrl = openAi.url();
        if (!wsUrl.contains("model=")) {
            wsUrl += (wsUrl.contains("?") ? "&" : "?") + "model=" + config.modelOr(openAi.model());
        }
        URI uri = URI.create(wsUrl);

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + openAi.apiKey())
                    .header("OpenAI-Beta", "realtime=v1")
                    .connectTimeout(Duration.ofSeconds(openAi.connectTimeoutSeconds()))
                    .buildAsync(uri, new OpenAiResponseHandler(config.channelId(), listener, alive, ready,
                            telephone, speakingItem, toolBatch))
                    .get(openAi.connectTimeoutSeconds(), TimeUnit.SECONDS);

            OpenAiRealtimeSession session = new OpenAiRealtimeSession(webSocket, alive, telephone, speakingItem,
                    toolBatch);
            session.send(sessionUpdateMessage(config, openAi));

            // The latch is also released when the socket closes or fails; that is no start.
            if (!ready.await(openAi.connectTimeoutSeconds(), TimeUnit.SECONDS) || !alive.get()) {
                session.close();
                throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_SETUP_FAILED,
                        "openai-realtime", "openai-realtime", openAi.connectTimeoutSeconds());
            }

            log.info("[{}] OpenAI Realtime session open (model={}, voice={}, audio={})",
                    config.channelId(), config.modelOr(openAi.model()), openAi.voice(), openAi.audioFormat());
            return session;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.REALTIME_PROVIDER_CONNECT_FAILED,
                    "openai-realtime", e, "openai-realtime", e.getMessage());
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
        String format = openAi.telephoneAudio() ? "g711_ulaw" : "pcm16";
        session.put("input_audio_format", format);
        session.put("output_audio_format", format);
        session.put("temperature", openAi.temperature());

        ObjectNode turnDetection = session.putObject("turn_detection");
        if ("semantic_vad".equalsIgnoreCase(openAi.turnDetection())) {
            turnDetection.put("type", "semantic_vad");
            turnDetection.put("eagerness", openAi.eagerness());
        } else {
            turnDetection.put("type", "server_vad");
            turnDetection.put("threshold", 0.5);
            turnDetection.put("prefix_padding_ms", 300);
            turnDetection.put("silence_duration_ms", 400);
        }
        if (openAi.noiseReduction() != null && !openAi.noiseReduction().isBlank()) {
            session.putObject("input_audio_noise_reduction").put("type", openAi.noiseReduction().trim());
        }

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            session.put("instructions", config.systemPrompt());
        }

        // What lands in call_transcript, and from there in the summary and the CRM note.
        // The language is a hint the transcriber otherwise has to guess from 8 kHz audio.
        ObjectNode inputTrans = session.putObject("input_audio_transcription");
        inputTrans.put("model", openAi.transcriptionModel());
        // Per-token log-probabilities of that transcript: the only confidence signal the
        // engine offers, read below to ask the caller to repeat a number it barely heard.
        session.putArray("include").add("item.input_audio_transcription.logprobs");
        String language = isoLanguage(config.language());
        if (language != null) {
            inputTrans.put("language", language);
        }

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
                } catch (Exception e) {
                    // Still offered, only without a schema the engine can check arguments against.
                    log.debug("Tool {} has an unreadable input schema: {}", tool.getToolDefinition().name(), e.getMessage());
                }
            }
        }

        return root.toString();
    }

    /** {@code uz-UZ} to {@code uz}; null for nothing usable. */
    /**
     * Mean token probability of a transcript, from the log-probabilities the session asked
     * for; 1.0 when the engine sent none, so a missing signal never reads as a bad one.
     */
    private static double transcriptConfidence(JsonNode logprobs) {
        if (logprobs == null || !logprobs.isArray() || logprobs.isEmpty()) {
            return 1.0;
        }
        double sum = 0;
        for (JsonNode token : logprobs) {
            sum += token.path("logprob").asDouble(0);
        }
        return Math.exp(sum / logprobs.size());
    }

    private static String isoLanguage(String bcp47) {
        if (bcp47 == null || bcp47.isBlank()) {
            return null;
        }
        String primary = bcp47.trim().split("[-_]")[0].toLowerCase(Locale.ROOT);
        return primary.length() == 2 ? primary : null;
    }

    private static final class OpenAiResponseHandler implements WebSocket.Listener {

        private final String channelId;
        private final RealtimeListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final boolean telephone;
        private final AtomicReference<String> speakingItem;
        private final ToolBatch toolBatch;
        private final StringBuilder message = new StringBuilder();
        private final StringBuilder botTranscript = new StringBuilder();

        private OpenAiResponseHandler(String channelId, RealtimeListener listener,
                                      AtomicBoolean alive, CountDownLatch ready, boolean telephone,
                                      AtomicReference<String> speakingItem, ToolBatch toolBatch) {
            this.channelId = channelId;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
            this.telephone = telephone;
            this.speakingItem = speakingItem;
            this.toolBatch = toolBatch;
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

        /**
         * Every message arrives on the WebSocket's own thread, which knows nothing about
         * the call — so the id goes in here, once, and everything below it (this class's
         * logs and the dialog engine's, reached through the listener) is filed under the
         * call it belongs to instead of {@code [call:none]}.
         */
        private void handle(String json) {
            CallMdc.with(channelId, () -> dispatch(json));
        }

        private void dispatch(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String type = node.path("type").asText("");

                switch (type) {
                    // Ready only once our session.update is applied: counting session.created
                    // too started the call before the instructions, voice and audio format
                    // were in place — and if the update was rejected, never with them at all.
                    case "session.updated" -> ready.countDown();
                    case "response.audio.delta" -> {
                        String itemId = node.path("item_id").asText("");
                        if (!itemId.isEmpty()) {
                            speakingItem.set(itemId);
                        }
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
                        JsonNode usage = node.path("response").path("usage");
                        if (usage.isObject()) {
                            listener.onUsage(usage.path("input_tokens").asLong(0),
                                    usage.path("output_tokens").asLong(0),
                                    usage.path("input_token_details").path("cached_tokens").asLong(0));
                        }
                        listener.onTurnComplete();
                        if (toolBatch.responseDone()) {
                            toolBatch.requestResponse.run(); // every result was in before the response ended
                        }
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        String transcript = node.path("transcript").asText("");
                        if (!transcript.isEmpty()) {
                            listener.onInputTranscript(transcript, true);
                            if (transcriptConfidence(node.path("logprobs")) < LOW_CONFIDENCE) {
                                listener.onLowConfidenceInput(transcript);
                            }
                        }
                    }
                    case "input_audio_buffer.speech_started" -> {
                        listener.onInterrupted();
                    }
                    case "response.function_call_arguments.done" -> {
                        String callId = node.path("call_id").asText("");
                        String name = node.path("name").asText("");
                        String args = node.path("arguments").asText("{}");
                        toolBatch.called(callId);
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
     * The function calls of the response in progress. The model may call several tools in
     * one response; asking for the next response after each result made it answer before
     * the other results were in (or be refused for having a response already active). So a
     * response.create goes out once the response has ended and every call it made has
     * its result — whichever of the two comes last triggers it.
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

        private boolean settleIfComplete() {
            if (!outstanding.isEmpty()) {
                return false;
            }
            called = false;
            responseEnded = false;
            return true;
        }
    }

    private static final class OpenAiRealtimeSession implements RealtimeSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final boolean telephone;
        private final AtomicReference<String> speakingItem;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;
        private final ToolBatch toolBatch;
        /** Writes queued on {@link #sendChain} and not yet done — audio is dropped past the bound. */
        private final java.util.concurrent.atomic.AtomicInteger pendingSends = new java.util.concurrent.atomic.AtomicInteger();
        /** About two seconds of 20 ms frames. */
        private static final int MAX_PENDING_AUDIO_SENDS = 100;

        private OpenAiRealtimeSession(WebSocket webSocket, AtomicBoolean alive, boolean telephone,
                                      AtomicReference<String> speakingItem, ToolBatch toolBatch) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.telephone = telephone;
            this.speakingItem = speakingItem;
            this.toolBatch = toolBatch;
            toolBatch.requestResponse = this::requestResponse;
        }

        @Override
        public void sendAudio(short[] pcm) {
            if (pcm == null || pcm.length == 0 || closed || !alive.get()) {
                return;
            }
            if (pendingSends.get() >= MAX_PENDING_AUDIO_SENDS) {
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
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "input_audio_buffer.append");
            root.put("audio", Base64.getEncoder().encodeToString(bytes));
            send(root.toString());
        }

        @Override
        public void sendUserText(String text) {
            if (!sendUserItem(text)) {
                return;
            }
            ObjectNode createResp = MAPPER.createObjectNode();
            createResp.put("type", "response.create");
            send(createResp.toString());
        }

        @Override
        public void sendContext(String text) {
            // The item alone, no response.create: the server VAD answers the caller's next
            // turn with it in the conversation.
            sendUserItem(text);
        }

        /** @return whether the item went out — false for blank text or a closed session */
        private boolean sendUserItem(String text) {
            if (text == null || text.isBlank() || closed || !alive.get()) {
                return false;
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
            return true;
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
            if (toolBatch.answered(callId)) {
                requestResponse();
            }
        }

        /** One response.create per tool batch — see {@link ToolBatch}. */
        private void requestResponse() {
            if (closed || !alive.get()) {
                return;
            }
            ObjectNode createResp = MAPPER.createObjectNode();
            createResp.put("type", "response.create");
            send(createResp.toString());
        }

        /**
         * The server keeps the whole reply it generated in the conversation, heard or not;
         * this cuts it back to what was played, so the model does not later build on a
         * sentence the caller interrupted before it was said.
         */
        @Override
        public void truncatePlayback(long heardMs) {
            String itemId = speakingItem.get();
            if (itemId == null || closed || !alive.get()) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "conversation.item.truncate");
            root.put("item_id", itemId);
            root.put("content_index", 0);
            root.put("audio_end_ms", Math.max(0, heardMs));
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
