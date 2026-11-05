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
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final String DEFAULT_MODEL = "gemini-3.5-transcribe-live";

    private final SttProperties sttProperties;
    private final SttMetrics metrics;
    private volatile HttpClient client;

    public GeminiSttProvider(SttProperties sttProperties, SttMetrics metrics, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    /**
     * Live transcription sessions stream for up to 10 minutes; {@link SttStreamBridge} swaps the
     * stream in a pause shortly before that instead of letting it end mid-sentence.
     */
    @Override
    public int maxStreamSeconds() {
        return 600;
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

        EouLatencyTracker eouLatency = new EouLatencyTracker();
        ResponseHandler handler = new ResponseHandler(languageCode, listener, alive, ready, eouLatency);
        int timeoutSeconds = sttProperties.gemini() != null ? sttProperties.gemini().connectTimeoutSeconds() : 10;

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .buildAsync(uri, handler)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            alive.set(true);

            // Send session setup message
            String setupPayload = buildSetupMessage(externalEndpointing, resolveModel(model), languageCode, alternativeLanguages, hints);
            webSocket.sendText(setupPayload, true).get(timeoutSeconds, TimeUnit.SECONDS);

            // Wait for setup acknowledgement
            if (!ready.await(timeoutSeconds, TimeUnit.SECONDS)) {
                alive.set(false);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "setup-timeout");
                throw new ExternalServiceException(ErrorCode.STT_GEMINI_SETUP_TIMEOUT, "gemini-stt", timeoutSeconds);
            }

            log.info("Gemini STT streaming session open (lang={}, model={}, endpointing={})",
                    languageCode, resolveModel(model), externalEndpointing ? "external" : "gemini");
            return new GeminiSttSession(webSocket, alive, metrics, externalEndpointing, eouLatency);
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
    private String buildSetupMessage(boolean externalEndpointing, String model, String languageCode,
                                     List<String> alternativeLanguages, List<String> hints) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode setup = root.putObject("setup");
        setup.put("model", "models/" + model);

        ObjectNode genConfig = setup.putObject("generationConfig");
        genConfig.putArray("responseModalities").add("TEXT");
        genConfig.put("temperature", 0.0);

        boolean isRussian = languageCode != null && languageCode.toLowerCase().startsWith("ru");
        String instruction = isRussian
                ? "You are an accurate, verbatim speech-to-text transcriber for a phone call in Uzbekistan. The speaker is speaking Russian. Transcribe speech accurately verbatim in Russian Cyrillic script. Never translate. Never output foreign words or foreign scripts. If you cannot recognize the audio, output nothing."
                : "You are an accurate, verbatim speech-to-text transcriber for a phone call in Uzbekistan. The speaker is speaking Uzbek (or occasionally Russian). Transcribe speech accurately verbatim in Uzbek Latin or Cyrillic script. Never translate. STRICTLY FORBIDDEN: outputting words from Vietnamese, Bengali, Hindi, English, Spanish, or other foreign languages. Never invent or hallucinate food names, lyrics, or phrases from other languages. Short utterances like 'ha', 'yo\\'q', 'xop', 'va alaykum assalom' are common Uzbek phrases. If speech is unclear, output nothing.";
        setup.putObject("systemInstruction").putArray("parts")
                .addObject().put("text", instruction);

        // This is what makes the server transcribe the audio at all.
        ObjectNode transcription = setup.putObject("inputAudioTranscription");
        ArrayNode langCodes = transcription.putArray("languageCodes");
        if (isRussian) {
            langCodes.add("ru-RU");
            langCodes.add("uz-UZ");
        } else {
            langCodes.add("uz-UZ");
            langCodes.add("ru-RU");
        }
        if (alternativeLanguages != null) {
            for (String alt : alternativeLanguages) {
                if (alt != null && !alt.isBlank()) {
                    String norm = alt.trim();
                    boolean exists = false;
                    for (int i = 0; i < langCodes.size(); i++) {
                        if (langCodes.get(i).asText().equalsIgnoreCase(norm)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        langCodes.add(norm);
                    }
                }
            }
        }

        ArrayNode vocab = transcription.putArray("customVocabulary");
        Set<String> added = new LinkedHashSet<>();
        List<String> defaultKeywords = List.of(
                "assalomu alaykum", "va alaykum assalom", "ha", "yo'q", "xop", "xo'p", "tushundim",
                "to'lov", "to'layman", "to'lab beraman", "to'ladim", "to'lay olmayman",
                "keyingi oy", "shu oy", "ertaga", "indin", "oylik", "uch oylik", "birdaniga",
                "bir million", "ikki million", "uch million", "besh yuz ming",
                "qarz", "qarzdorlik", "shartnoma", "so'm", "bank", "kredit", "grafik",
                "operator", "qayta telefon", "vaqtim yo'q", "keyinroq", "alo"
        );
        for (String w : defaultKeywords) {
            if (added.add(w.toLowerCase())) {
                vocab.add(w);
            }
        }
        if (hints != null) {
            for (String h : hints) {
                if (h != null && !h.isBlank() && added.add(h.toLowerCase().trim())) {
                    vocab.add(h.trim());
                }
            }
        }
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
            if (script == Character.UnicodeScript.LATIN) {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isUzbekModifier(c)) {
                    continue;
                }
                return false;
            }
            if (script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.CYRILLIC) {
                return false;
            }
        }
        return anyLetter;
    }

    private static boolean isUzbekModifier(char c) {
        return c == '\'' || c == '`' || c == 'ʻ' || c == 'ʼ' || c == '’' || c == '‘';
    }

    /**
     * Handles incoming WebSocket messages from Gemini Bidi streaming endpoint.
     */
    private final class ResponseHandler extends JsonWebSocketListener {

        private final String languageCode;
        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch ready;
        private final EouLatencyTracker eouLatency;

        /** The last interim of the utterance in hand — what a garbage final falls back to. */
        private String lastInterim;

        private ResponseHandler(String languageCode, TranscriptListener listener,
                                AtomicBoolean alive, CountDownLatch ready, EouLatencyTracker eouLatency) {
            this.languageCode = languageCode;
            this.listener = listener;
            this.alive = alive;
            this.ready = ready;
            this.eouLatency = eouLatency;
        }

        @Override
        protected void onMessage(String json) {
            try {
                JsonNode node = objectMapper.readTree(json);
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
                if (inExpectedScript(interim, languageCode)) {
                    lastInterim = interim;
                    listener.onTranscript(interim, false, 0.0f);
                } else {
                    log.debug("Gemini STT dropped interim transcription with foreign script: {}", interim);
                }
            }

            String finalText = content.path("inputTranscription").path("text").asText("").trim();
            if (finalText.isEmpty()) {
                return;
            }
            if (!inExpectedScript(finalText, languageCode)) {
                if (lastInterim == null || !inExpectedScript(lastInterim, languageCode)) {
                    log.warn("Gemini STT final is not in the {} script and there is no valid interim to fall back to — dropped: {}",
                            languageCode, finalText);
                    lastInterim = null;
                    return;
                }
                log.warn("Gemini STT final is not in the {} script ('{}') — using the last interim: {}",
                        languageCode, finalText, lastInterim);
                finalText = lastInterim;
            }
            lastInterim = null;
            eouLatency.finalReceived(metrics, GeminiSttProvider.this.name());
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
    private final class GeminiSttSession implements SttSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final SttMetrics metrics;
        /** Whether this side brackets utterances with activityStart/activityEnd. */
        private final boolean externalEndpointing;
        private final EouLatencyTracker eouLatency;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile boolean closed;
        /** An activityStart has been sent and its activityEnd has not. */
        private boolean inActivity;
        /** Writes queued on {@link #sendChain} and not yet done; past the bound audio frames are dropped. */
        private final java.util.concurrent.atomic.AtomicInteger pendingSends = new java.util.concurrent.atomic.AtomicInteger();
        /** About one second of 20 ms frames. */
        private static final int MAX_PENDING_SENDS = 50;

        private GeminiSttSession(WebSocket webSocket, AtomicBoolean alive, SttMetrics metrics,
                                 boolean externalEndpointing, EouLatencyTracker eouLatency) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.metrics = metrics;
            this.externalEndpointing = externalEndpointing;
            this.eouLatency = eouLatency;
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
                ObjectNode start = objectMapper.createObjectNode();
                start.putObject("realtimeInput").putObject("activityStart");
                send(start.toString());
            }
            if (pendingSends.get() >= MAX_PENDING_SENDS) {
                return; // a stalled socket: the frame is dropped rather than queued without end
            }
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode audio = root.putObject("realtimeInput").putObject("audio");
            audio.put("mimeType", "audio/pcm;rate=16000");
            audio.put("data", Base64.getEncoder().encodeToString(pcm16le));
            send(root.toString());
        }

        /** A stalled uplink shows here instead of as a caller who says nothing (SttSession#isReady). */
        @Override
        public boolean isReady() {
            return pendingSends.get() < MAX_PENDING_SENDS;
        }

        @Override
        public synchronized void endUtterance() {
            if (closed || !alive.get() || !externalEndpointing || !inActivity) {
                return;
            }
            inActivity = false;
            eouLatency.eouSent();
            ObjectNode root = objectMapper.createObjectNode();
            root.putObject("realtimeInput").putObject("activityEnd");
            send(root.toString());
        }

        private void send(String payload) {
            pendingSends.incrementAndGet();
            sendChain = sendChain
                    .thenCompose(ignored -> webSocket.sendText(payload, true))
                    .handle((sent, e) -> {
                        pendingSends.decrementAndGet();
                        if (e != null) {
                            alive.set(false);
                            metrics.sttError();
                            log.warn("Gemini STT send failed: {}", e.getMessage());
                        }
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
