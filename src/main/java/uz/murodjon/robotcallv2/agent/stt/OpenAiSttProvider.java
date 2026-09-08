package uz.murodjon.robotcallv2.agent.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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
 * OpenAI realtime Speech-to-Text provider ({@code gpt-live-transcribe}).
 *
 * <p>Opens a transcription-intent Realtime WebSocket, streams base64 PCM in
 * {@code input_audio_buffer.append} events and reads
 * {@code conversation.item.input_audio_transcription.delta} as interims and
 * {@code ...completed} as finals.
 *
 * <p>Who ends an utterance follows the same rule as every other provider here. With
 * external endpointing the gate decides: {@code turn_detection} is set to {@code null} so
 * the server runs no detector of its own, and {@link SttSession#endUtterance()} sends the
 * {@code input_audio_buffer.commit} that produces the final. Without a gate the server's
 * detection is left in place and commits on the silence it hears.
 *
 * <p>Untested against the live endpoint — written from the published protocol. The first
 * call on it should be watched for a {@code session.updated} that never arrives, which is
 * what a rejected setup looks like from this side.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.open-ai.api-key:}'.isBlank() || !'${OPENAI_API_KEY:}'.isBlank()")
public class OpenAiSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSttProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private volatile HttpClient client;

    public OpenAiSttProvider(SttProperties sttProperties, VoiceMetrics metrics) {
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        if (resolveApiKey().isBlank()) {
            log.warn("OpenAI STT selected but no API key is available (voice-agent.stt.open-ai.api-key or OPENAI_API_KEY)");
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings().connectTimeoutSeconds()))
                .build();
        log.info("OpenAI STT provider ready (model={}, sampleRate={}Hz)", settings().model(), sampleRate());
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public int sampleRate() {
        return settings().sampleRate();
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, List.of());
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing,
                                  List<String> hints) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, hints, null);
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing,
                                  List<String> hints, String model) {
        HttpClient current = client;
        String apiKey = resolveApiKey();
        if (current == null || apiKey.isBlank()) {
            throw new ExternalServiceException(ErrorCode.STT_OPENAI_CLIENT_UNAVAILABLE, "openai-stt");
        }

        OpenAiSttProperties settings = settings();
        int timeoutSeconds = settings.connectTimeoutSeconds();
        AtomicBoolean alive = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(1);
        ResponseHandler handler = new ResponseHandler(listener, alive, ready);

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("OpenAI-Beta", "realtime=v1")
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .buildAsync(URI.create(settings.url()), handler)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            alive.set(true);
            webSocket.sendText(sessionUpdate(settings, resolveModel(model, settings), languageCode,
                            alternativeLanguages, hints, externalEndpointing), true)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            if (!ready.await(timeoutSeconds, TimeUnit.SECONDS)) {
                alive.set(false);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "setup-timeout");
                throw new ExternalServiceException(ErrorCode.STT_OPENAI_CONNECT_FAILED, "openai-stt",
                        "session setup was not acknowledged within " + timeoutSeconds + "s");
            }

            log.info("OpenAI STT streaming session open (lang={}, model={}, endpointing={})",
                    languageCode, resolveModel(model, settings), externalEndpointing ? "external" : "openai");
            return new OpenAiSttSession(webSocket, alive, metrics, externalEndpointing);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.sttError();
            throw new ExternalServiceException(ErrorCode.STT_OPENAI_CONNECT_FAILED, "openai-stt", e, e.getMessage());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            metrics.sttError();
            throw new ExternalServiceException(ErrorCode.STT_OPENAI_CONNECT_FAILED, "openai-stt", e, e.getMessage());
        }
    }

    /**
     * The transcription session's configuration.
     *
     * <p>{@code languages} is sent as the call's language plus whatever else the campaign
     * says the caller may speak — the same list every other provider here gets, and the
     * reason a uz-UZ call does not come back as Turkish. {@code prompt} carries the
     * {@link SttHints} the turn already assembles (the client's name, the services they
     * will be pointed at), which is what this API offers in place of a phrase list.
     */
    private static String sessionUpdate(OpenAiSttProperties settings, String model, String languageCode,
                                        List<String> alternativeLanguages, List<String> hints,
                                        boolean externalEndpointing) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "session.update");
        ObjectNode session = root.putObject("session");
        session.put("type", "transcription");
        ObjectNode input = session.putObject("audio").putObject("input");

        ObjectNode format = input.putObject("format");
        format.put("type", "audio/pcm");
        format.put("rate", settings.sampleRate());

        ObjectNode transcription = input.putObject("transcription");
        transcription.put("model", model);
        if (languageCode != null && !languageCode.isBlank()) {
            ArrayNode languages = transcription.putArray("languages");
            languages.add(languageCode.trim());
            if (alternativeLanguages != null) {
                for (String alternative : alternativeLanguages) {
                    if (alternative != null && !alternative.isBlank()
                            && !alternative.trim().equalsIgnoreCase(languageCode.trim())) {
                        languages.add(alternative.trim());
                    }
                }
            }
        }
        if (hints != null && !hints.isEmpty()) {
            transcription.put("prompt", String.join(", ", hints));
        }

        if (externalEndpointing) {
            // The gate commits each utterance; the server's own detector would wait for a
            // silence it is never sent, and nothing would ever be finalized.
            input.putNull("turn_detection");
        }
        return root.toString();
    }

    private String resolveApiKey() {
        OpenAiSttProperties settings = sttProperties.openAi();
        if (settings != null && settings.apiKey() != null && !settings.apiKey().isBlank()) {
            return settings.apiKey().trim();
        }
        String env = System.getenv("OPENAI_API_KEY");
        return env != null ? env.trim() : "";
    }

    private static String resolveModel(String requested, OpenAiSttProperties settings) {
        return (requested != null && !requested.isBlank()) ? requested.trim() : settings.model();
    }

    private OpenAiSttProperties settings() {
        OpenAiSttProperties configured = sttProperties.openAi();
        return configured != null ? configured : new OpenAiSttProperties(null, null, null, 0, 0);
    }

    /** Reads the session's server events. */
    private final class ResponseHandler implements WebSocket.Listener {

        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final StringBuilder messageBuffer = new StringBuilder();

        private ResponseHandler(TranscriptListener listener, AtomicBoolean alive, CountDownLatch ready) {
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            messageBuffer.append(StandardCharsets.UTF_8.decode(data));
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        private void handleMessage(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String type = node.path("type").asText("");
                switch (type) {
                    case "session.created", "session.updated", "transcription_session.updated" -> ready.countDown();
                    case "conversation.item.input_audio_transcription.delta" -> {
                        String delta = node.path("delta").asText("").trim();
                        if (!delta.isEmpty()) {
                            listener.onTranscript(delta, false, 0.0f);
                        }
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        String text = node.path("transcript").asText("").trim();
                        if (!text.isEmpty()) {
                            listener.onTranscript(text, true, 0.0f);
                        }
                    }
                    case "error" -> {
                        // Surfaced rather than swallowed: a rejected session.update arrives
                        // here and is otherwise indistinguishable from a caller who says
                        // nothing for the rest of the call.
                        log.warn("OpenAI STT error event: {}", node.path("error"));
                        ready.countDown();
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse OpenAI STT message: {}", e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            ready.countDown();
            log.debug("OpenAI STT stream closed ({}: {})", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            ready.countDown();
            metrics.sttError();
            log.warn("OpenAI STT stream error: {}", error.getMessage());
        }
    }

    /** An open transcription session; audio in, commits on the gate's signal. */
    private static final class OpenAiSttSession implements SttSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final VoiceMetrics metrics;
        private final boolean externalEndpointing;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile boolean closed;
        /** Audio has been appended since the last commit — there is something to finalize. */
        private boolean pendingAudio;

        private OpenAiSttSession(WebSocket webSocket, AtomicBoolean alive, VoiceMetrics metrics,
                                 boolean externalEndpointing) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.metrics = metrics;
            this.externalEndpointing = externalEndpointing;
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16le) {
            if (closed || !alive.get() || pcm16le == null || pcm16le.length == 0) {
                return;
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "input_audio_buffer.append");
            root.put("audio", Base64.getEncoder().encodeToString(pcm16le));
            pendingAudio = true;
            send(root.toString());
        }

        @Override
        public synchronized void endUtterance() {
            if (closed || !alive.get() || !externalEndpointing || !pendingAudio) {
                return;
            }
            pendingAudio = false;
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "input_audio_buffer.commit");
            send(root.toString());
        }

        private void send(String payload) {
            sendChain = sendChain
                    .thenCompose(ignored -> webSocket.sendText(payload, true))
                    .thenAccept(sent -> {
                    })
                    .exceptionally(e -> {
                        alive.set(false);
                        metrics.sttError();
                        log.warn("OpenAI STT send failed: {}", e.getMessage());
                        return null;
                    });
        }

        @Override
        public boolean isAlive() {
            return alive.get() && !closed;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            alive.set(false);
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "session closed");
            } catch (Exception e) {
                log.debug("OpenAI STT close ignored: {}", e.getMessage());
            }
        }
    }
}
