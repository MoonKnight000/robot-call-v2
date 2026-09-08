package uz.murodjon.robotcallv2.agent.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Gemini streaming Speech-to-Text provider ({@code gemini-3.5-transcribe-live}).
 *
 * <p>Streams 16 kHz PCM audio into Gemini's Live WebSocket endpoint
 * ({@code BidiGenerateContent}) and delivers the server's own transcription of that
 * audio to {@link TranscriptListener}: {@code interimInputTranscription} as interims,
 * {@code inputTranscription} as the final. The setup asks for it with
 * {@code inputAudioTranscription}; without that field nothing is ever transcribed.
 *
 * <p>Who says the utterance is over depends on the call. With external endpointing the
 * gate does: automatic activity detection is switched off and the session brackets each
 * utterance with {@code activityStart}/{@code activityEnd} — the only signal this model
 * finalizes on (a {@code clientContent.turnComplete} produces no final at all). Without
 * a gate the server's own detection stays on and finalizes after the silence it hears.
 *
 * <p>Measured on a recorded uz-UZ call (2026-09-05): the final arrives ~350 ms after
 * {@code activityEnd}, the interims are usable, and the final is sometimes emitted in an
 * unrelated script (Bengali, Gurmukhi) for the same audio the interim had as Uzbek. Such
 * a final is discarded in favour of the last interim. No setup field pins the language:
 * {@code customVocabulary} and a language code under {@code inputAudioTranscription} are
 * rejected, and {@code speechConfig.languageCode} changed nothing.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.gemini.api-key:}'.isBlank() || !'${GEMINI_API_KEY:}'.isBlank()")
public class GeminiSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiSttProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final String DEFAULT_MODEL = "gemini-3.5-transcribe-live";

    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private volatile HttpClient client;

    public GeminiSttProvider(SttProperties sttProperties, VoiceMetrics metrics) {
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            log.warn("Gemini STT selected but no API key is available (voice-agent.stt.gemini.api-key or GEMINI_API_KEY)");
            return;
        }
        int timeoutSeconds = sttProperties.gemini() != null ? sttProperties.gemini().connectTimeoutSeconds() : 10;
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        log.info("Gemini STT provider ready (model={}, sampleRate={}Hz)",
                resolveModel(null), sampleRate());
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public int sampleRate() {
        return (sttProperties.gemini() != null && sttProperties.gemini().sampleRate() > 0)
                ? sttProperties.gemini().sampleRate()
                : DEFAULT_SAMPLE_RATE;
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
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.STT_GEMINI_CLIENT_UNAVAILABLE, "gemini-stt");
        }

        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new ExternalServiceException(ErrorCode.STT_GEMINI_CLIENT_UNAVAILABLE, "gemini-stt");
        }

        URI uri = buildWebSocketUri(apiKey);
        AtomicBoolean alive = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(1);

        ResponseHandler handler = new ResponseHandler(languageCode, listener, alive, ready);
        int timeoutSeconds = sttProperties.gemini() != null ? sttProperties.gemini().connectTimeoutSeconds() : 10;

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .buildAsync(uri, handler)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            alive.set(true);

            // Send session setup message
            String setupPayload = buildSetupMessage(externalEndpointing, resolveModel(model));
            webSocket.sendText(setupPayload, true).get(timeoutSeconds, TimeUnit.SECONDS);

            // Wait for setup acknowledgement
            if (!ready.await(timeoutSeconds, TimeUnit.SECONDS)) {
                alive.set(false);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "setup-timeout");
                throw new ExternalServiceException(ErrorCode.STT_GEMINI_CONNECT_FAILED,
                        "gemini-stt", "session setup was not acknowledged within " + timeoutSeconds + "s");
            }

            log.info("Gemini STT streaming session open (lang={}, model={}, endpointing={})",
                    languageCode, resolveModel(model), externalEndpointing ? "external" : "gemini");
            return new GeminiSttSession(webSocket, alive, metrics, externalEndpointing);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.sttError();
            throw new ExternalServiceException(ErrorCode.STT_GEMINI_CONNECT_FAILED, "gemini-stt", e, e.getMessage());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            metrics.sttError();
            throw new ExternalServiceException(ErrorCode.STT_GEMINI_CONNECT_FAILED, "gemini-stt", e, e.getMessage());
        }
    }

    private String resolveApiKey() {
        if (sttProperties.gemini() != null && sttProperties.gemini().apiKey() != null && !sttProperties.gemini().apiKey().isBlank()) {
            return sttProperties.gemini().apiKey().trim();
        }
        String env = System.getenv("GEMINI_API_KEY");
        return env != null ? env.trim() : "";
    }

    private String resolveModel(String requested) {
        if (requested != null && !requested.isBlank()) {
            String r = requested.trim();
            return r.startsWith("models/") ? r.substring("models/".length()) : r;
        }
        if (sttProperties.gemini() != null && sttProperties.gemini().model() != null && !sttProperties.gemini().model().isBlank()) {
            String m = sttProperties.gemini().model().trim();
            return m.startsWith("models/") ? m.substring("models/".length()) : m;
        }
        return DEFAULT_MODEL;
    }

    private URI buildWebSocketUri(String apiKey) {
        String base = (sttProperties.gemini() != null && sttProperties.gemini().url() != null && !sttProperties.gemini().url().isBlank())
                ? sttProperties.gemini().url().trim()
                : "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + "key=" + apiKey);
    }

    /**
     * The session setup. Hints are deliberately not sent: the Live API has no vocabulary
     * field ({@code customVocabulary} closes the socket with "Cannot find field"), and a
     * setup that fails costs the whole call its recognition.
     */
    private String buildSetupMessage(boolean externalEndpointing, String model) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode setup = root.putObject("setup");
        setup.put("model", "models/" + model);

        ObjectNode genConfig = setup.putObject("generationConfig");
        genConfig.putArray("responseModalities").add("TEXT");
        // This is what makes the server transcribe the audio at all.
        setup.putObject("inputAudioTranscription");
        if (externalEndpointing) {
            // The gate says where an utterance ends (activityStart/activityEnd); the
            // server's own detector would wait for a silence the gate never sends it.
            setup.putObject("realtimeInputConfig")
                    .putObject("automaticActivityDetection")
                    .put("disabled", true);
        }
        return root.toString();
    }

    /**
     * Whether a final is in the script this call's language is written in. The model
     * occasionally finalizes Uzbek phone audio as Bengali or Gurmukhi; a transcript the
     * dialog cannot read is worse than the interim it replaces.
     */
    static boolean inExpectedScript(String text, String languageCode) {
        boolean anyLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            anyLetter = true;
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.CYRILLIC) {
                return false;
            }
        }
        return anyLetter;
    }

    /**
     * Handles incoming WebSocket messages from Gemini Bidi streaming endpoint.
     */
    private final class ResponseHandler implements WebSocket.Listener {

        private final String languageCode;
        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;

        private final StringBuilder messageBuffer = new StringBuilder();
        /** The last interim of the utterance in hand — what a garbage final falls back to. */
        private String lastInterim;

        private ResponseHandler(String languageCode, TranscriptListener listener,
                                AtomicBoolean alive, CountDownLatch ready) {
            this.languageCode = languageCode;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String fullMessage = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(fullMessage);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            messageBuffer.append(StandardCharsets.UTF_8.decode(data));
            if (last) {
                String fullMessage = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(fullMessage);
            }
            webSocket.request(1);
            return null;
        }

        private void handleMessage(String json) {
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
                if (node.has("goAway")) {
                    log.info("Gemini STT received goAway: {}", node.get("goAway"));
                    alive.set(false);
                }
            } catch (Exception e) {
                log.warn("Failed to parse Gemini STT message: {}", e.getMessage());
            }
        }

        private void handleServerContent(JsonNode content) {
            String interim = content.path("interimInputTranscription").path("text").asText("").trim();
            if (!interim.isEmpty()) {
                lastInterim = interim;
                listener.onTranscript(interim, false, 0.0f);
            }

            String finalText = content.path("inputTranscription").path("text").asText("").trim();
            if (finalText.isEmpty()) {
                return;
            }
            if (!inExpectedScript(finalText, languageCode)) {
                if (lastInterim == null) {
                    log.warn("Gemini STT final is not in the {} script and there is no interim to fall back to — dropped: {}",
                            languageCode, finalText);
                    return;
                }
                log.warn("Gemini STT final is not in the {} script ('{}') — using the last interim: {}",
                        languageCode, finalText, lastInterim);
                finalText = lastInterim;
            }
            lastInterim = null;
            // No confidence from this API; 0 keeps the low-confidence repair from firing on it.
            listener.onTranscript(finalText, true, 0.0f);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            ready.countDown();
            log.debug("Gemini STT stream closed ({}: {})", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            ready.countDown();
            metrics.sttError();
            log.warn("Gemini STT stream error: {}", error.getMessage());
        }
    }

    /**
     * Active Gemini streaming STT session sending raw PCM audio.
     */
    private static final class GeminiSttSession implements SttSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final VoiceMetrics metrics;
        /** Whether this side brackets utterances with activityStart/activityEnd. */
        private final boolean externalEndpointing;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile boolean closed;
        /** An activityStart has been sent and its activityEnd has not. */
        private boolean inActivity;

        private GeminiSttSession(WebSocket webSocket, AtomicBoolean alive, VoiceMetrics metrics,
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
            if (externalEndpointing && !inActivity) {
                // First audio of an utterance: with automatic detection off the server
                // ignores audio outside an activity.
                inActivity = true;
                ObjectNode start = MAPPER.createObjectNode();
                start.putObject("realtimeInput").putObject("activityStart");
                send(start.toString());
            }
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode audio = root.putObject("realtimeInput").putObject("audio");
            audio.put("mimeType", "audio/pcm;rate=16000");
            audio.put("data", Base64.getEncoder().encodeToString(pcm16le));
            send(root.toString());
        }

        @Override
        public synchronized void endUtterance() {
            if (closed || !alive.get() || !externalEndpointing || !inActivity) {
                return;
            }
            inActivity = false;
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("realtimeInput").putObject("activityEnd");
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
                        log.warn("Gemini STT send failed: {}", e.getMessage());
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
                log.debug("Gemini STT close ignored: {}", e.getMessage());
            }
        }
    }
}
