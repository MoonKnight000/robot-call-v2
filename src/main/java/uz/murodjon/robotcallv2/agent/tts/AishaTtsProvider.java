package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.Resampler;
import uz.murodjon.robotcallv2.agent.provider.JsonWebSocketListener;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aisha realtime text-to-speech over its WebSocket API (PROJECT.md §2.5). A line is sent
 * as JSON ({@code request_id}, {@code text}, {@code language}, {@code speaker_id},
 * {@code speed}) and comes back as a metadata frame followed by one binary frame holding
 * a 16 kHz mono WAV, which is parsed and downsampled to the 8 kHz the RTP path plays.
 *
 * <p>One socket is shared by every call and reopened when it drops: Aisha asks for
 * exactly that ("send multiple text turns over one persistent connection"), and a fresh
 * TLS handshake per line would be paid inside a live turn. Replies are matched back to
 * their request by {@code request_id}, so concurrent calls do not have to queue behind
 * each other.
 *
 * <p>Aisha returns the whole utterance in one frame, so there is nothing to stream
 * ahead: {@link #synthesizeStreaming} stays with the default single-chunk delivery.
 *
 * <p>Registered whenever {@code voice-agent.tts.aisha.api-key} is set — a company picks
 * this provider per-call via {@code ai_agent.tts_provider}, it does
 * not have to be the process-wide {@code voice-agent.tts.provider} default.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.aisha.api-key:}'.isBlank()")
public class AishaTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(AishaTtsProvider.class);
    private final ObjectMapper objectMapper;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Rate of the WAV Aisha returns; anything else means the API changed under us. */
    private static final int AISHA_SAMPLE_RATE = 16000;

    private final TtsProperties ttsProperties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final BlockingQueue<AishaConnection> idlePool = new LinkedBlockingQueue<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    public AishaTtsProvider(TtsProperties ttsProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ttsProperties = ttsProperties;
    }

    @PostConstruct
    public void init() {
        AishaTtsProperties aisha = ttsProperties.aisha();
        if (aisha == null || aisha.apiKey() == null || aisha.apiKey().isBlank()) {
            log.warn("Aisha TTS selected but voice-agent.tts.aisha.api-key is blank — synthesis will fail");
            return;
        }
        try {
            // Warm up one connection in the pool at startup so the first call does not pay the handshake.
            AishaConnection conn = borrowConnection();
            returnToPool(conn);
            log.info("Aisha TTS pool ready (url={}, speaker='{}', maxPoolSize={})",
                    aisha.url(), aisha.speaker(), aisha.poolSize());
        } catch (RuntimeException e) {
            // Not fatal: subsequent synthesis attempts will connect. Failing startup here
            // would take the whole application down for a provider outage.
            log.warn("Aisha TTS could not connect at startup: {}", e.getMessage());
        }
    }

    @Override
    public String name() {
        return "aisha";
    }

    @Override
    public boolean supports(String language) {
        // Aisha synthesizes Uzbek, Russian and English; the mood/speaker only exists for
        // Uzbek, which the request builder handles.
        return language != null
                && (language.startsWith("uz") || language.startsWith("ru") || language.startsWith("en"));
    }

    @Override
    public short[] synthesize(String text, String language, String voice) {
        return synthesize(text, language, voice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
        AishaTtsProperties aisha = ttsProperties.aisha();
        String requestId = UUID.randomUUID().toString();
        String requestJson = request(requestId, text, language, voice, style).toString();
        AishaConnection conn = borrowConnection();
        try {
            byte[] wav = conn.requestAudio(requestJson, aisha.requestTimeoutMs());
            returnToPool(conn);
            return toTelephonePcm(wav);
        } catch (ExternalServiceException e) {
            discardConnection(conn);
            throw e;
        } catch (InterruptedException e) {
            discardConnection(conn);
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "aisha-tts", e, e.getMessage());
        } catch (TimeoutException e) {
            discardConnection(conn);
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_TIMEOUT, "aisha-tts", e,
                    aisha.requestTimeoutMs());
        } catch (Exception e) {
            discardConnection(conn);
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "aisha-tts", e, e.getMessage());
        }
    }

    /** The JSON one line is synthesized from. */
    private ObjectNode request(String requestId, String text, String language, String voice,
                               EffectiveVoiceSettings style) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("request_id", requestId);
        request.put("text", text);
        request.put("language", languageTag(language));
        request.put("speaker_id", speakerFor(language, voice, style));
        if (style != null && style.speed() != null) {
            request.put("speed", style.speed());
        }
        return request;
    }

    /** Aisha takes a bare language ({@code uz}, {@code ru}, {@code en}), not a BCP-47 tag. */
    private String languageTag(String language) {
        String tag = (language == null || language.isBlank()) ? ttsProperties.defaultLanguage() : language;
        int dash = tag.indexOf('-');
        return (dash > 0 ? tag.substring(0, dash) : tag).toLowerCase(Locale.ROOT);
    }

    /**
     * The mood this line is spoken with: the mood the turn asked for, then the campaign's
     * catalog voice, then the per-language override, then the configured default.
     */
    private String speakerFor(String language, String voice, EffectiveVoiceSettings style) {
        if (style != null && style.role() != null && !style.role().isBlank()) {
            String mood = normalizeAishaSpeaker(style.role());
            List<String> moods = ttsProperties.aisha().moods();
            if (moods == null || moods.isEmpty() || moods.contains(mood)) {
                return mood;
            }
            log.debug("Aisha has no mood '{}' — speaking the line as '{}'", mood, ttsProperties.aisha().speaker());
        }
        if (voice != null && !voice.isBlank()) {
            return normalizeAishaSpeaker(voice);
        }
        Map<String, String> speakers = ttsProperties.aisha().speakers();
        if (speakers != null && language != null) {
            String exact = speakers.get(language);
            if (exact != null && !exact.isBlank()) {
                return normalizeAishaSpeaker(exact);
            }
        }
        return normalizeAishaSpeaker(ttsProperties.aisha().speaker());
    }

    private static String normalizeAishaSpeaker(String speaker) {
        if (speaker == null || speaker.isBlank()) {
            return "neutral";
        }
        String lower = speaker.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("cheerful") || lower.contains("happy") || lower.contains("good") || lower.contains("friendly")) {
            return "cheerful";
        }
        if (lower.contains("sad")) {
            return "sad";
        }
        if (lower.contains("neutral") || lower.contains("gulnoza") || lower.contains("strict")) {
            return "neutral";
        }
        return lower;
    }

    /** Parse the returned WAV and bring it down to the 8 kHz the RTP path plays. */
    private static short[] toTelephonePcm(byte[] wav) {
        WavAudio audio;
        try {
            audio = WavReader.read(wav);
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_AUDIO_PARSE_FAILED, "aisha-tts", e, e.getMessage());
        }
        if (audio.sampleRate() == 8000) {
            return audio.samples();
        }
        if (audio.sampleRate() != AISHA_SAMPLE_RATE) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SAMPLE_RATE_UNEXPECTED, "aisha-tts",
                    audio.sampleRate());
        }
        return Resampler.downsample16kTo8k(audio.samples(), audio.samples().length);
    }

    private AishaConnection borrowConnection() {
        AishaTtsProperties aisha = ttsProperties.aisha();
        if (aisha == null || aisha.apiKey() == null || aisha.apiKey().isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CLIENT_UNAVAILABLE, "aisha-tts");
        }
        int maxPoolSize = aisha.poolSize();

        // 1. Try taking an idle connection
        AishaConnection conn = idlePool.poll();
        while (conn != null) {
            if (conn.isUsable()) {
                return conn;
            }
            conn.close();
            connectionCount.decrementAndGet();
            conn = idlePool.poll();
        }

        // 2. If room in pool, create a new connection
        if (connectionCount.get() < maxPoolSize) {
            return openConnection(aisha);
        }

        // 3. Otherwise wait for one to be returned
        try {
            conn = idlePool.poll(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CONNECT_FAILED, "aisha-tts", e, e.getMessage());
        }
        if (conn != null && conn.isUsable()) {
            return conn;
        }
        if (conn != null) {
            conn.close();
            connectionCount.decrementAndGet();
        }
        return openConnection(aisha);
    }

    private AishaConnection openConnection(AishaTtsProperties aisha) {
        connectionCount.incrementAndGet();
        try {
            return new AishaConnection(client, URI.create(aisha.url() + "?token=" + aisha.apiKey()));
        } catch (Exception e) {
            connectionCount.decrementAndGet();
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CONNECT_FAILED, "aisha-tts", e, e.getMessage());
        }
    }

    private void returnToPool(AishaConnection conn) {
        if (conn != null && conn.isUsable()) {
            idlePool.offer(conn);
        } else if (conn != null) {
            discardConnection(conn);
        }
    }

    private void discardConnection(AishaConnection conn) {
        if (conn != null) {
            conn.close();
            connectionCount.decrementAndGet();
        }
    }

    @PreDestroy
    public void close() {
        AishaConnection conn;
        while ((conn = idlePool.poll()) != null) {
            conn.close();
        }
    }

    /**
     * An isolated WebSocket connection to Aisha TTS. Dedicated to a single line synthesis
     * while borrowed, avoiding frame interleaving, shared-memory races, and global locks.
     */
    private final class AishaConnection extends JsonWebSocketListener implements AutoCloseable {

        private final WebSocket webSocket;
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private volatile CompletableFuture<byte[]> currentAudioFuture;
        private volatile boolean closed;

        private AishaConnection(HttpClient client, URI uri) throws Exception {
            this.webSocket = client.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(uri, this)
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        public byte[] requestAudio(String json, long timeoutMs) throws Exception {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            this.currentAudioFuture = future;
            webSocket.sendText(json, true).get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        protected void onMessage(String json) {
            try {
                JsonNode node = objectMapper.readTree(json);
                String type = node.path("type").asText("");
                if ("error".equals(type) || node.hasNonNull("error")) {
                    String reason = node.path("message").asText(node.path("error").asText("synthesis failed"));
                    CompletableFuture<byte[]> f = currentAudioFuture;
                    if (f != null) {
                        f.completeExceptionally(new IllegalStateException(reason));
                    }
                }
            } catch (Exception e) {
                log.warn("Aisha TTS sent unparseable message: {}", e.getMessage());
            }
        }

        @Override
        protected void onBinaryFrame(ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            audioBuffer.writeBytes(chunk);
            if (last) {
                byte[] wav = audioBuffer.toByteArray();
                audioBuffer.reset();
                CompletableFuture<byte[]> f = currentAudioFuture;
                if (f != null) {
                    f.complete(wav);
                }
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed = true;
            CompletableFuture<byte[]> f = currentAudioFuture;
            if (f != null && !f.isDone()) {
                f.completeExceptionally(new IllegalStateException("Socket closed by server: " + reason));
            }
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed = true;
            CompletableFuture<byte[]> f = currentAudioFuture;
            if (f != null && !f.isDone()) {
                f.completeExceptionally(error);
            }
        }

        public boolean isUsable() {
            return !closed && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
        }

        @Override
        public void close() {
            closed = true;
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "pool closed");
            } catch (Exception ignored) {
            }
        }
    }
}
