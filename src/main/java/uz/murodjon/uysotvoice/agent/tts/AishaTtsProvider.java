package uz.murodjon.uysotvoice.agent.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.rtp.WavAudio;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 * this provider per-call via {@code engine_config.tts_provider} (§11 settings), it does
 * not have to be the process-wide {@code voice-agent.tts.provider} default.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.aisha.api-key:}'.isBlank()")
public class AishaTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(AishaTtsProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Rate of the WAV Aisha returns; anything else means the API changed under us. */
    private static final int AISHA_SAMPLE_RATE = 16000;

    private final TtsProperties props;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** Lines waiting for their audio, by {@code request_id}. */
    private final Map<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    /** The same ids in send order — how a reply is matched when it carries no id. */
    private final ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();

    private volatile WebSocket webSocket;
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

    public AishaTtsProvider(TtsProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        AishaTtsProperties aisha = props.aisha();
        if (aisha == null || aisha.apiKey() == null || aisha.apiKey().isBlank()) {
            log.warn("Aisha TTS selected but voice-agent.tts.aisha.api-key is blank — synthesis will fail");
            return;
        }
        try {
            // Connect at startup so the first call of a deploy does not pay the handshake.
            connect();
            log.info("Aisha TTS ready (url={}, speaker='{}')", aisha.url(), aisha.speaker());
        } catch (RuntimeException e) {
            // Not fatal: the first synthesis reconnects. Failing startup here would take
            // the whole dialer down for a provider outage.
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
        AishaTtsProperties aisha = props.aisha();
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<byte[]> audio = new CompletableFuture<>();
        pending.put(requestId, audio);
        order.add(requestId);
        try {
            send(request(requestId, text, language, voice, style).toString());
            byte[] wav = audio.get(aisha.requestTimeoutMs(), TimeUnit.MILLISECONDS);
            return toTelephonePcm(wav);
        } catch (ExternalServiceException e) {
            // Already carries its own code (connect / audio parse) — rewrapping it as a
            // synthesis failure would hide which half actually broke.
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "aisha-tts", e, e.getMessage());
        } catch (TimeoutException e) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "aisha-tts", e,
                    "no audio within " + aisha.requestTimeoutMs() + " ms");
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "aisha-tts", e, e.getMessage());
        } finally {
            pending.remove(requestId);
            order.remove(requestId);
        }
    }

    /** The JSON one line is synthesized from. */
    private ObjectNode request(String requestId, String text, String language, String voice,
                               EffectiveVoiceSettings style) {
        ObjectNode request = MAPPER.createObjectNode();
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
        String tag = (language == null || language.isBlank()) ? props.defaultLanguage() : language;
        int dash = tag.indexOf('-');
        return (dash > 0 ? tag.substring(0, dash) : tag).toLowerCase(Locale.ROOT);
    }

    /**
     * The mood this line is spoken with: dynamic role from style, then the campaign's catalog voice,
     * then the per-language override, then the configured default.
     */
    private String speakerFor(String language, String voice, EffectiveVoiceSettings style) {
        if (style != null && style.role() != null && !style.role().isBlank()) {
            return normalizeAishaSpeaker(style.role());
        }
        if (voice != null && !voice.isBlank()) {
            return normalizeAishaSpeaker(voice);
        }
        Map<String, String> speakers = props.aisha().speakers();
        if (speakers != null && language != null) {
            String exact = speakers.get(language);
            if (exact != null && !exact.isBlank()) {
                return normalizeAishaSpeaker(exact);
            }
        }
        return normalizeAishaSpeaker(props.aisha().speaker());
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
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_AUDIO_PARSE_FAILED, "aisha-tts",
                    "unexpected sample rate " + audio.sampleRate());
        }
        return Resampler.downsample16kTo8k(audio.samples(), audio.samples().length);
    }

    /**
     * Write one request to the shared socket. The JDK client forbids a second send before
     * the previous one completes, so sends are chained; the wait is on the audio coming
     * back anyway.
     */
    private void send(String json) throws Exception {
        WebSocket current = connect();
        CompletableFuture<Void> sent;
        synchronized (this) {
            sendChain = sendChain
                    .thenCompose(ignored -> current.sendText(json, true))
                    .thenAccept(ws -> {
                    });
            sent = sendChain;
            // A failed send must not poison every later line, so the chain continues from
            // a clean future while the failure is reported to this caller.
            sendChain = sent.exceptionally(e -> null);
        }
        sent.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    /** The shared socket, opened (or reopened after a drop) on demand. */
    private synchronized WebSocket connect() {
        WebSocket current = webSocket;
        if (current != null && !current.isOutputClosed() && !current.isInputClosed()) {
            return current;
        }
        AishaTtsProperties aisha = props.aisha();
        if (aisha == null || aisha.apiKey() == null || aisha.apiKey().isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CONNECT_FAILED, "aisha-tts", "no api key");
        }
        URI uri = URI.create(aisha.url() + "?token=" + aisha.apiKey());
        try {
            WebSocket opened = client.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(uri, new AudioHandler())
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            sendChain = CompletableFuture.completedFuture(null);
            webSocket = opened;
            return opened;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CONNECT_FAILED, "aisha-tts", e, e.getMessage());
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_CONNECT_FAILED, "aisha-tts", e, e.getMessage());
        }
    }

    /** Fail everything still waiting — their socket is gone and no audio is coming. */
    private void abandonPending(String reason) {
        for (Map.Entry<String, CompletableFuture<byte[]>> entry : pending.entrySet()) {
            entry.getValue().completeExceptionally(new IllegalStateException(reason));
        }
        pending.clear();
        order.clear();
    }

    @PreDestroy
    public void close() {
        WebSocket current = webSocket;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "app shutdown").get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("Aisha socket close failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Reads the metadata frame + audio frame pairs off the shared socket and hands each
     * line's audio to the caller waiting for it.
     */
    private final class AudioHandler implements WebSocket.Listener {

        private final StringBuilder message = new StringBuilder();
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        /** The id the metadata frame announced, claimed by the binary frame after it. */
        private volatile String currentRequestId;

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String json = message.toString();
                message.setLength(0);
                handle(json);
            }
            socket.request(1);
            return null;
        }

        private void handle(String json) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String requestId = node.path("request_id").asText("");
                if (!requestId.isBlank()) {
                    currentRequestId = requestId;
                }
                String type = node.path("type").asText("");
                if ("error".equals(type) || node.hasNonNull("error")) {
                    String reason = node.path("message").asText(node.path("error").asText("synthesis failed"));
                    CompletableFuture<byte[]> waiting = claim();
                    if (waiting != null) {
                        waiting.completeExceptionally(new IllegalStateException(reason));
                    }
                    log.warn("Aisha TTS error: {}", reason);
                }
            } catch (Exception e) {
                log.warn("Aisha TTS sent an unparseable message ({}): {}", e.getMessage(), json);
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            audio.writeBytes(chunk);
            if (last) {
                byte[] wav = audio.toByteArray();
                audio.reset();
                CompletableFuture<byte[]> waiting = claim();
                if (waiting != null) {
                    waiting.complete(wav);
                } else {
                    log.warn("Aisha TTS delivered {} bytes nobody was waiting for", wav.length);
                }
            }
            socket.request(1);
            return null;
        }

        /**
         * The request this frame belongs to: the id the metadata frame carried, or —
         * if it carried none — the oldest line still waiting, since Aisha answers in
         * the order it was asked.
         */
        private CompletableFuture<byte[]> claim() {
            String requestId = currentRequestId;
            currentRequestId = null;
            if (requestId == null) {
                requestId = order.poll();
            } else {
                order.remove(requestId);
            }
            return requestId == null ? null : pending.remove(requestId);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            log.info("Aisha TTS socket closed: {} ({})", statusCode, reason);
            abandonPending("socket closed by server: " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            log.warn("Aisha TTS socket error: {}", error.getMessage());
            abandonPending("socket error: " + error.getMessage());
        }
    }
}
