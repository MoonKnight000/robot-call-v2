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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Deepgram Streaming Speech-to-Text Provider (Nova-2 / Nova-3 WebSocket API).
 *
 * <p>Provides industry-leading ultra-fast speech recognition (&lt;200ms latency)
 * with interim results and automatic punctuation.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.stt.deepgram.api-key:}'.isBlank()")
public class DeepgramSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSttProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private volatile HttpClient client;

    public DeepgramSttProvider(SttProperties sttProperties, VoiceMetrics metrics) {
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        DeepgramSttProperties d = sttProperties.deepgram();
        if (d == null || d.apiKey() == null || d.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(d.connectTimeoutSeconds()))
                .build();
        log.info("Deepgram STT ready (model={}, sampleRate={})", d.model(), d.sampleRate());
    }

    @Override
    public String name() {
        return "deepgram";
    }

    @Override
    public int sampleRate() {
        DeepgramSttProperties d = sttProperties.deepgram();
        return d != null ? d.sampleRate() : 8000;
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, List.of(), null);
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing,
                                  List<String> hints, String model) {
        HttpClient current = client;
        DeepgramSttProperties d = sttProperties.deepgram();
        if (current == null || d == null) {
            throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_CLIENT_UNAVAILABLE, "deepgram");
        }

        String lang = languageCode != null && languageCode.length() >= 2 ? languageCode.substring(0, 2) : "ru";
        String chosenModel = (model != null && !model.isBlank()) ? model.trim() : d.model();
        // utterance_end_ms is Deepgram's backstop for speech_final, which it may never send
        // when the caller's audio stays noisy after they stop; it needs interim results.
        String wsUrl = String.format("%s?model=%s&language=%s&encoding=linear16&sample_rate=%d&channels=1&interim_results=%s&smart_format=true&endpointing=300%s",
                d.url(), chosenModel, lang, d.sampleRate(), d.interimResults(),
                d.interimResults() ? "&utterance_end_ms=1000" : "");

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch connected = new CountDownLatch(1);

        try {
            WebSocket webSocket = current.newWebSocketBuilder()
                    .header("Authorization", "Token " + d.apiKey())
                    .connectTimeout(Duration.ofSeconds(d.connectTimeoutSeconds()))
                    .buildAsync(URI.create(wsUrl), new DeepgramResponseHandler(listener, alive, connected, metrics, d.interimResults()))
                    .get(d.connectTimeoutSeconds(), TimeUnit.SECONDS);

            if (!connected.await(d.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                webSocket.abort();
                throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_CONNECT_TIMEOUT, "deepgram",
                        d.connectTimeoutSeconds());
            }

            log.info("Opened Deepgram STT stream for lang={} (model={})", lang, chosenModel);
            return new DeepgramSttSession(webSocket, alive, externalEndpointing);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_STREAM_FAILED, "deepgram", e, e.getMessage());
        }
    }

    /**
     * Turns Deepgram's result stream into one final per utterance. {@code is_final} only
     * means "this stretch of audio will not be re-transcribed" and arrives several times
     * within one utterance; forwarding each as a final split one sentence into several
     * turns. Finalized stretches are collected here and emitted as the utterance's final
     * on {@code speech_final}, on an {@code UtteranceEnd} message, or on the result of a
     * {@code Finalize} request ({@code from_finalize}).
     */
    private static final class DeepgramResponseHandler implements WebSocket.Listener {

        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch connected;
        private final VoiceMetrics metrics;
        private final boolean interimResults;
        private final StringBuilder message = new StringBuilder();
        /** Finalized stretches of the utterance in progress. Only touched from onText, which is sequential. */
        private final StringBuilder utterance = new StringBuilder();
        private float utteranceConfidence = 1.0f;

        private DeepgramResponseHandler(TranscriptListener listener, AtomicBoolean alive,
                                        CountDownLatch connected, VoiceMetrics metrics, boolean interimResults) {
            this.listener = listener;
            this.alive = alive;
            this.connected = connected;
            this.metrics = metrics;
            this.interimResults = interimResults;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            connected.countDown();
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String complete = message.toString();
                message.setLength(0);
                parseAndDispatch(complete);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void parseAndDispatch(String json) {
            try {
                JsonNode root = MAPPER.readTree(json);
                if ("UtteranceEnd".equals(root.path("type").asText(""))) {
                    emitUtterance();
                    return;
                }
                JsonNode alternatives = root.path("channel").path("alternatives");
                if (!alternatives.isArray() || alternatives.isEmpty()) {
                    return;
                }
                JsonNode best = alternatives.get(0);
                String transcript = best.path("transcript").asText("");
                float confidence = (float) best.path("confidence").asDouble(1.0);
                if (root.path("is_final").asBoolean(false)) {
                    if (!transcript.isBlank()) {
                        if (!utterance.isEmpty()) {
                            utterance.append(' ');
                        }
                        utterance.append(transcript.trim());
                        utteranceConfidence = Math.min(utteranceConfidence, confidence);
                    }
                    if (root.path("speech_final").asBoolean(false) || root.path("from_finalize").asBoolean(false)) {
                        emitUtterance();
                    } else if (interimResults && !utterance.isEmpty()) {
                        listener.onTranscript(utterance.toString(), false, utteranceConfidence);
                    }
                } else if (interimResults && !transcript.isBlank()) {
                    String partial = utterance.isEmpty() ? transcript : utterance + " " + transcript.trim();
                    listener.onTranscript(partial, false, confidence);
                }
            } catch (Exception e) {
                log.debug("Failed to parse Deepgram message: {}", e.getMessage());
            }
        }

        private void emitUtterance() {
            if (utterance.isEmpty()) {
                return;
            }
            listener.onTranscript(utterance.toString(), true, utteranceConfidence);
            utterance.setLength(0);
            utteranceConfidence = 1.0f;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            alive.set(false);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            alive.set(false);
            log.warn("Deepgram WebSocket error: {}", error.getMessage());
        }
    }

    /**
     * Every write goes through one chain: the JDK WebSocket refuses a send while another is
     * still in flight, and handing it 20 ms frames straight from the RTP thread lost them
     * with an IllegalStateException nobody saw. The chain is bounded — past
     * {@link #MAX_PENDING_SENDS} frames behind, audio is dropped and {@link #isReady()}
     * says so instead of the backlog growing without end.
     */
    private static final class DeepgramSttSession implements SttSession {

        /** About one second of 20 ms frames. */
        private static final int MAX_PENDING_SENDS = 50;
        /** Deepgram closes a stream after ~10 s without data; a KeepAlive every 5 s of silence prevents it. */
        private static final long KEEP_ALIVE_AFTER_NANOS = TimeUnit.SECONDS.toNanos(5);

        private final WebSocket webSocket;
        private final AtomicBoolean alive;
        private final boolean externalEndpointing;
        private final AtomicInteger pendingSends = new AtomicInteger();
        private final Object sendLock = new Object();
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private volatile long lastSentNanos = System.nanoTime();

        private DeepgramSttSession(WebSocket webSocket, AtomicBoolean alive, boolean externalEndpointing) {
            this.webSocket = webSocket;
            this.alive = alive;
            this.externalEndpointing = externalEndpointing;
            // Audio stops reaching the recognizer while the VAD gate holds it back during
            // silence, which is exactly when Deepgram would time the stream out.
            Thread.ofVirtual().name("deepgram-keepalive").start(this::keepAlive);
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            if (!alive.get()) {
                return;
            }
            if (pendingSends.get() >= MAX_PENDING_SENDS) {
                log.debug("Deepgram uplink {} frames behind — dropping one", MAX_PENDING_SENDS);
                return;
            }
            enqueue(socket -> socket.sendBinary(ByteBuffer.wrap(pcm16le), true));
        }

        /** With external endpointing the caller's silence is ours to call: ask Deepgram to finalize now. */
        @Override
        public void endUtterance() {
            if (externalEndpointing && alive.get()) {
                enqueue(socket -> socket.sendText("{\"type\":\"Finalize\"}", true));
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
                            .thenCompose(ignored -> webSocket.sendText("{\"type\":\"CloseStream\"}", true))
                            .thenCompose(socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
                            .handle((socket, e) -> {
                                if (e != null) {
                                    log.debug("Deepgram close not sent: {}", e.getMessage());
                                }
                                return null;
                            });
                }
            }
        }

        private void enqueue(Function<WebSocket, CompletableFuture<WebSocket>> write) {
            pendingSends.incrementAndGet();
            lastSentNanos = System.nanoTime();
            synchronized (sendLock) {
                sendChain = sendChain
                        .thenCompose(ignored -> write.apply(webSocket))
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
                        enqueue(socket -> socket.sendText("{\"type\":\"KeepAlive\"}", true));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
