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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final SttProperties props;
    private final VoiceMetrics metrics;
    private volatile HttpClient client;

    public DeepgramSttProvider(SttProperties props, VoiceMetrics metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        DeepgramSttProperties d = props.deepgram();
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
        DeepgramSttProperties d = props.deepgram();
        return d != null ? d.sampleRate() : 8000;
    }

    @Override
    public SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                  TranscriptListener listener, boolean externalEndpointing) {
        HttpClient current = client;
        DeepgramSttProperties d = props.deepgram();
        if (current == null || d == null) {
            throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_CONNECT_FAILED, "deepgram client not initialized");
        }

        String lang = languageCode != null && languageCode.length() >= 2 ? languageCode.substring(0, 2) : "ru";
        String wsUrl = String.format("%s?model=%s&language=%s&encoding=linear16&sample_rate=%d&channels=1&interim_results=%s&smart_format=true&endpointing=300",
                d.url(), d.model(), lang, d.sampleRate(), d.interimResults());

        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch connected = new CountDownLatch(1);

        try {
            WebSocket ws = current.newWebSocketBuilder()
                    .header("Authorization", "Token " + d.apiKey())
                    .connectTimeout(Duration.ofSeconds(d.connectTimeoutSeconds()))
                    .buildAsync(URI.create(wsUrl), new DeepgramResponseHandler(listener, alive, connected, metrics, d.interimResults()))
                    .get(d.connectTimeoutSeconds(), TimeUnit.SECONDS);

            if (!connected.await(d.connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                ws.abort();
                throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_STREAM_FAILED, "deepgram connect timeout");
            }

            log.info("Opened Deepgram STT stream for lang={} (model={})", lang, d.model());
            return new DeepgramSttSession(ws, alive);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.STT_DEEPGRAM_STREAM_FAILED, "deepgram", e, e.getMessage());
        }
    }

    private static final class DeepgramResponseHandler implements WebSocket.Listener {

        private final TranscriptListener listener;
        private final AtomicBoolean alive;
        private final CountDownLatch connected;
        private final VoiceMetrics metrics;
        private final boolean interimResults;
        private final StringBuilder message = new StringBuilder();

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
                JsonNode channel = root.path("channel");
                JsonNode alternatives = channel.path("alternatives");
                if (alternatives.isArray() && !alternatives.isEmpty()) {
                    JsonNode best = alternatives.get(0);
                    String transcript = best.path("transcript").asText("");
                    double confidence = best.path("confidence").asDouble(1.0);
                    boolean isFinal = root.path("is_final").asBoolean(false);

                    if (!transcript.isBlank()) {
                        if (isFinal) {
                            listener.onTranscript(transcript, true, (float) confidence);
                        } else if (interimResults) {
                            listener.onTranscript(transcript, false, (float) confidence);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse Deepgram message: {}", e.getMessage());
            }
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

    private static final class DeepgramSttSession implements SttSession {

        private final WebSocket ws;
        private final AtomicBoolean alive;

        private DeepgramSttSession(WebSocket ws, AtomicBoolean alive) {
            this.ws = ws;
            this.alive = alive;
        }

        @Override
        public void sendAudio(byte[] pcm16le) {
            if (!alive.get()) {
                return;
            }
            ws.sendBinary(ByteBuffer.wrap(pcm16le), true);
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }

        @Override
        public void close() {
            if (alive.compareAndSet(true, false)) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
                } catch (Exception ignored) {
                }
            }
        }
    }
}
