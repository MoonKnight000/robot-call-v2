package uz.murodjon.robotcallv2.agent.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aisha realtime speech-to-text over its WebSocket API (PROJECT.md §2.4). Each call
 * opens {@code wss://back.aisha.group/api/v1/stt/realtime?format=pcm&token=...} and
 * pushes raw 16 kHz mono s16le frames up it; transcripts come back as JSON
 * ({@code {"type":"transcription","text":...,"partial":...}}).
 *
 * <p>Aisha endpoints for itself — the protocol has no external end-of-utterance signal,
 * only {@code {"event":"end"}}, which ends the whole session — so a stream opened with
 * external endpointing ignores {@link SttSession#endUtterance()}, exactly as the
 * {@link SttProvider} contract allows. VAD gating still applies: it decides which audio
 * is worth streaming (and paying for), just not when the utterance is over.
 *
 * <p>Registered whenever {@code voice-agent.stt.aisha.api-key} is set — a company picks
 * this provider per-call via {@code engine_config.stt_provider} (§11 settings), it does
 * not have to be the process-wide {@code voice-agent.stt.provider} default.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.aisha.api-key:}'.isBlank()")
public class AishaSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(AishaSttProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The rate Aisha's {@code format=pcm} stream is defined at — 16 kHz mono s16le. Not
     * a config knob: 8 kHz audio pushed into a stream declared as 16 kHz is accepted on
     * the wire and comes back as gibberish, so the pipeline resamples instead
     * ({@link SttStreamBridge}).
     */
    private static final int SAMPLE_RATE = 16000;

    private static final String END_EVENT = "{\"event\":\"end\"}";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private volatile HttpClient client;

    public AishaSttProvider(SttProperties sttProperties, VoiceMetrics metrics) {
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        AishaSttProperties aisha = sttProperties.aisha();
        if (aisha == null || aisha.apiKey() == null || aisha.apiKey().isBlank()) {
            log.warn("Aisha STT selected but voice-agent.stt.aisha.api-key is blank — recognition will fail");
            return;
        }
        client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        log.info("Aisha STT ready (url={}, sampleRate={}, interim={})",
                aisha.url(), SAMPLE_RATE, aisha.interimResults());
        EndpointingProperties endpointing = sttProperties.endpointing();
        if (endpointing != null && endpointing.enabled()) {
            // Aisha only finalizes when it hears the silence after an utterance, and
            // external endpointing shuts the gate a few hundred ms after speech stops —
            // so the trailing silence it needs never reaches it and the turn hangs with
            // no final. This is a run-breaking combination, not a tuning preference.
            log.warn("voice-agent.stt.endpointing is enabled but Aisha endpoints for itself — "
                    + "set STT_ENDPOINTING=false and STT_VAD_POST_ROLL_MS back to ~1500 for this provider");
        }
    }

    @Override
    public String name() {
        return "aisha";
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        // Aisha recognizes the language it is given; it has no detection of its own, so a
        // bilingual call is only served by the other providers.
        HttpClient current = client;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.STT_AISHA_CONNECT_FAILED, "aisha-stt", "no api key");
        }
        AishaSttProperties aisha = sttProperties.aisha();
        AtomicBoolean alive = new AtomicBoolean(true);
        URI uri = URI.create(aisha.url() + "?format=pcm&token=" + aisha.apiKey());
        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(uri, new ResponseHandler(languageCode, listener, alive))
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            log.info("Opened Aisha STT stream for {} (endpointing: aisha)", languageCode);
            return new AishaSttSession(webSocket, alive);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.STT_AISHA_CONNECT_FAILED, "aisha-stt", e, e.getMessage());
        } catch (Exception e) {
            metrics.sttError();
            throw new ExternalServiceException(ErrorCode.STT_AISHA_CONNECT_FAILED, "aisha-stt", e, e.getMessage());
        }
    }

    /** Maps Aisha JSON messages (transcription / session_started / error) onto the listener. */
    private final class ResponseHandler implements WebSocket.Listener {

        private final String language;
        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        /** Text arrives in fragments; a message is only parseable once {@code last} is set. */
        private final StringBuilder message = new StringBuilder();

        private ResponseHandler(String language, TranscriptListener listener, AtomicBoolean alive) {
            this.language = language;
            this.listener = listener;
            this.alive = alive;
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

        private void handle(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String type = node.path("type").asText("");
                switch (type) {
                    case "transcription" -> emit(node);
                    case "error" -> {
                        metrics.sttError();
                        alive.set(false);
                        log.warn("Aisha STT error ({}): {} {}", language,
                                node.path("code").asText(""), node.path("message").asText(""));
                    }
                    // session_started carries the audio budget Aisha lets this stream spend.
                    case "session_started" -> log.debug("Aisha STT session {} started, {}s allowed",
                            node.path("session_id").asText(""), node.path("allowed_audio_seconds").asText(""));
                    default -> log.debug("Aisha STT message ignored: {}", json);
                }
            } catch (Exception e) {
                log.warn("Aisha STT sent an unparseable message ({}): {}", e.getMessage(), json);
            }
        }

        private void emit(JsonNode node) {
            String text = node.path("text").asText("");
            boolean partial = node.path("partial").asBoolean(false);
            if (text.isBlank() || (partial && !sttProperties.aisha().interimResults())) {
                return;
            }
            // Aisha reports no per-alternative confidence, so 0 here means "not reported"
            // — the same as the Yandex provider.
            listener.onTranscript(text, !partial, 0f);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // Recognition results are JSON; nothing binary is expected on this stream.
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                // 1008 is Aisha closing it: bad token, spent balance or a rejected format.
                log.warn("Aisha STT stream closed ({}): {} {}", language, statusCode, reason);
            } else {
                log.debug("Aisha STT stream closed ({})", language);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            metrics.sttError();
            alive.set(false);
            log.warn("Aisha STT stream error ({}): {}", language, error.getMessage());
        }
    }

    /**
     * Pushes audio frames into the socket. The JDK client forbids a second send before
     * the previous one has completed, so sends are chained instead of fired in parallel;
     * a failed send means the stream is gone, which {@link SttStreamBridge} handles by
     * reopening.
     */
    private static final class AishaSttSession implements SttSession {

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private boolean closed;

        private AishaSttSession(WebSocket webSocket, AtomicBoolean alive) {
            this.webSocket = webSocket;
            this.alive = alive;
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16le) {
            if (closed || !alive.get()) {
                return;
            }
            sendChain = sendChain
                    .thenCompose(ignored -> webSocket.sendBinary(ByteBuffer.wrap(pcm16le), true))
                    .thenAccept(sent -> {
                    })
                    .exceptionally(e -> {
                        alive.set(false);
                        return null;
                    });
        }

        @Override
        public boolean isAlive() {
            return !closed && alive.get();
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            alive.set(false);
            // Tell Aisha the audio is over so it finalizes and settles the session, then
            // close the socket whether or not that landed.
            webSocket.sendText(END_EVENT, true)
                    .thenCompose(ws -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "call ended"))
                    .exceptionally(e -> {
                        webSocket.abort();
                        return null;
                    });
        }
    }
}
