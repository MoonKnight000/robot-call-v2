package uz.murodjon.robotcallv2.agent.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.net.URI;
import java.net.URLEncoder;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ElevenLabs Scribe realtime speech-to-text ({@code scribe_v2_realtime} over WebSocket).
 *
 * <p>Audio goes up as base64 PCM inside {@code input_audio_chunk} JSON messages; the server
 * answers with {@code partial_transcript} (interim) and {@code committed_transcript} (final of
 * one segment). Who closes a segment depends on endpointing: with external endpointing the
 * stream is opened with {@code commit_strategy=manual} and {@link SttSession#endUtterance()}
 * commits it; otherwise Scribe's own VAD commits on silence ({@code commit_strategy=vad}).
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.eleven-labs.api-key:}'.isBlank()")
public class ElevenLabsSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsSttProvider.class);
    /** Realtime keyterm limits: at most 50 terms, 20 characters each. */
    private static final int MAX_KEYTERMS = 50;
    private static final int MAX_KEYTERM_LENGTH = 20;

    private final SttProperties sttProperties;
    private final ObjectMapper objectMapper;
    private volatile HttpClient client;

    public ElevenLabsSttProvider(SttProperties sttProperties, ObjectMapper objectMapper) {
        this.sttProperties = sttProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        ElevenLabsSttProperties settings = sttProperties.elevenLabs();
        if (settings == null || settings.apiKey() == null || settings.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();
        log.info("ElevenLabs STT ready (model={}, sampleRate={})", settings.model(), settings.sampleRate());
    }

    @Override
    public String name() {
        return "elevenlabs";
    }

    @Override
    public int sampleRate() {
        ElevenLabsSttProperties settings = sttProperties.elevenLabs();
        return settings != null ? settings.sampleRate() : 16000;
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, List.of(), null);
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
        ElevenLabsSttProperties settings = sttProperties.elevenLabs();
        if (current == null || settings == null) {
            throw new ExternalServiceException(ErrorCode.STT_ELEVENLABS_CLIENT_UNAVAILABLE, "elevenlabs-stt");
        }

        String lang = languageCode != null && languageCode.length() >= 2 ? languageCode.substring(0, 2) : "uz";
        String chosenModel = model != null && !model.isBlank() ? model.trim() : settings.model();
        String wsUrl = buildUrl(settings, chosenModel, lang, externalEndpointing, hints);

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> rejection = new AtomicReference<>();

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("xi-api-key", settings.apiKey())
                    .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                    .buildAsync(URI.create(wsUrl), new ElevenLabsResponseHandler(listener, alive, started, rejection))
                    .get(settings.connectTimeoutSeconds(), TimeUnit.SECONDS);

            if (!started.await(settings.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                webSocket.abort();
                throw new ExternalServiceException(ErrorCode.STT_ELEVENLABS_CONNECT_TIMEOUT, "elevenlabs-stt",
                        settings.connectTimeoutSeconds());
            }
            if (rejection.get() != null) {
                webSocket.abort();
                throw new ExternalServiceException(ErrorCode.STT_ELEVENLABS_SESSION_REJECTED, "elevenlabs-stt",
                        rejection.get());
            }

            log.info("Opened ElevenLabs STT stream for lang={} (model={}, commit={})",
                    lang, chosenModel, externalEndpointing ? "manual" : "vad");
            return new ElevenLabsSttSession(webSocket, alive, externalEndpointing, settings.sampleRate());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.STT_ELEVENLABS_STREAM_FAILED, "elevenlabs-stt", e, e.getMessage());
        }
    }

    private static String buildUrl(ElevenLabsSttProperties settings, String model, String lang,
                                   boolean externalEndpointing, List<String> hints) {
        StringBuilder url = new StringBuilder(settings.url())
                .append("?model_id=").append(encode(model))
                .append("&audio_format=pcm_").append(settings.sampleRate())
                .append("&language_code=").append(encode(lang))
                .append("&commit_strategy=").append(externalEndpointing ? "manual" : "vad");
        if (hints != null) {
            hints.stream()
                    .filter(hint -> hint != null && !hint.isBlank() && hint.trim().length() <= MAX_KEYTERM_LENGTH)
                    .map(String::trim)
                    .distinct()
                    .limit(MAX_KEYTERMS)
                    .forEach(hint -> url.append("&keyterms=").append(encode(hint)));
        }
        return url.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * {@code partial_transcript} carries the whole uncommitted segment so far, so it is
     * forwarded as is; {@code committed_transcript} closes that segment and is the final.
     * Error messages are logged — when one is fatal the server closes the socket, and
     * {@link #onClose} is what marks the session dead.
     */
    private final class ElevenLabsResponseHandler extends JsonWebSocketListener {

        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch started;
        private final AtomicReference<String> rejection;

        private ElevenLabsResponseHandler(TranscriptListener listener, AtomicBoolean alive,
                                          CountDownLatch started, AtomicReference<String> rejection) {
            this.listener = listener;
            this.alive = alive;
            this.started = started;
            this.rejection = rejection;
        }

        @Override
        protected void onBinaryFrame(ByteBuffer data, boolean last) {
            // ElevenLabs answers in text frames only.
        }

        @Override
        protected void onMessage(String json) {
            try {
                JsonNode root = objectMapper.readTree(json);
                String type = root.path("message_type").asText("");
                switch (type) {
                    case "session_started" -> started.countDown();
                    case "partial_transcript" -> {
                        String text = root.path("text").asText("");
                        if (!text.isBlank()) {
                            listener.onTranscript(text.trim(), false, 1.0f);
                        }
                    }
                    case "committed_transcript" -> {
                        String text = root.path("text").asText("");
                        if (!text.isBlank()) {
                            listener.onTranscript(text.trim(), true, 1.0f);
                        }
                    }
                    case "warning" -> log.debug("ElevenLabs STT warning: {}", root.path("warning").asText(""));
                    default -> {
                        if (root.has("error")) {
                            String error = type + ": " + root.path("error").asText("");
                            log.warn("ElevenLabs STT error {}", error);
                            if (started.getCount() > 0) {
                                rejection.set(error);
                                started.countDown();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse ElevenLabs message: {}", e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            if (started.getCount() > 0) {
                rejection.set("closed " + statusCode + " " + reason);
                started.countDown();
            }
            return super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            log.warn("ElevenLabs STT WebSocket error: {}", error.getMessage());
        }
    }

    /**
     * Writes go through one bounded chain, as in {@code DeepgramSttProvider}: the JDK WebSocket
     * refuses a send while another is in flight, and past {@link #MAX_PENDING_SENDS} frames
     * behind audio is dropped and {@link #isReady()} says so.
     */
    private final class ElevenLabsSttSession implements SttSession {

        /** About one second of 20 ms frames. */
        private static final int MAX_PENDING_SENDS = 50;
        /**
         * The protocol has no keep-alive message, and the VAD gate holds audio back during
         * silence; 100 ms of silence after 5 s without a send keeps the socket from idling out.
         */
        private static final long KEEP_ALIVE_AFTER_NANOS = TimeUnit.SECONDS.toNanos(5);

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final boolean externalEndpointing;
        private final int sampleRate;
        private final byte[] keepAliveSilence;
        private final AtomicInteger pendingSends = new AtomicInteger();
        private final Object sendLock = new Object();
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile long lastSentNanos = System.nanoTime();

        private ElevenLabsSttSession(WebSocket webSocket, AtomicBoolean alive, boolean externalEndpointing,
                                     int sampleRate) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.externalEndpointing = externalEndpointing;
            this.sampleRate = sampleRate;
            this.keepAliveSilence = new byte[sampleRate / 10 * 2];
            Thread.ofVirtual().name("elevenlabs-stt-keepalive").start(this::keepAlive);
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            if (!alive.get()) {
                return;
            }
            if (pendingSends.get() >= MAX_PENDING_SENDS) {
                log.debug("ElevenLabs STT uplink {} frames behind — dropping one", MAX_PENDING_SENDS);
                return;
            }
            enqueue(chunk(pcm16le, false));
        }

        /** With external endpointing the caller's silence is ours to call: commit the segment now. */
        @Override
        public void endUtterance() {
            if (externalEndpointing && alive.get()) {
                enqueue(chunk(new byte[0], true));
            }
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }

        @Override
        public boolean isReady() {
            return pendingSends.get() < MAX_PENDING_SENDS;
        }

        @Override
        public void close() {
            if (alive.compareAndSet(true, false)) {
                synchronized (sendLock) {
                    sendChain = sendChain
                            .thenCompose(ignored -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
                            .handle((socket, e) -> {
                                if (e != null) {
                                    log.debug("ElevenLabs STT close not sent: {}", e.getMessage());
                                }
                                return null;
                            });
                }
            }
        }

        private String chunk(byte[] pcm16le, boolean commit) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("message_type", "input_audio_chunk");
            root.put("audio_base_64", Base64.getEncoder().encodeToString(pcm16le));
            root.put("commit", commit);
            root.put("sample_rate", sampleRate);
            return root.toString();
        }

        private void enqueue(String payload) {
            pendingSends.incrementAndGet();
            lastSentNanos = System.nanoTime();
            synchronized (sendLock) {
                sendChain = sendChain
                        .thenCompose(ignored -> webSocket.sendText(payload, true))
                        .handle((socket, e) -> {
                            pendingSends.decrementAndGet();
                            if (e != null) {
                                alive.set(false);
                            }
                            return null;
                        });
            }
        }

        private void keepAlive() {
            try {
                while (alive.get()) {
                    Thread.sleep(1000);
                    if (alive.get() && System.nanoTime() - lastSentNanos >= KEEP_ALIVE_AFTER_NANOS) {
                        enqueue(chunk(keepAliveSilence, false));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
