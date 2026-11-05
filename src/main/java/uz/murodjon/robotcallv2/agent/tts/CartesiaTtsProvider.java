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
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cartesia Sonic Ultra-Fast TTS Provider (<90ms TTFB).
 *
 * <p>Synthesizes direct 8 kHz LINEAR16 PCM telephony audio over HTTP/2 streaming bytes,
 * bypassing heavy container parsing for near-instant turnaround. The response body is read
 * as it arrives ({@link #synthesizeStreaming}), so the first audio reaches the caller when
 * Cartesia has produced it, not when the whole sentence is done.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.cartesia.api-key:}'.isBlank()")
public class CartesiaTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(CartesiaTtsProvider.class);

    /** Cartesia's API version for every call; the version also decides the request schema. */
    private static final String API_VERSION = "2026-08-14";
    /** About 40 ms of 8 kHz audio per read. */
    private static final int READ_BUFFER_BYTES = 640;
    /** How much of a vendor error body is worth logging. */
    private static final int ERROR_BODY_LOG_BYTES = 2048;

    private final ObjectMapper objectMapper;

    private final TtsProperties ttsProperties;
    private final TtsStreamProperties streamProperties;
    private volatile HttpClient client;
    private volatile CartesiaWebSocketClient webSocketClient;

    public CartesiaTtsProvider(TtsProperties ttsProperties, ObjectMapper objectMapper,
                               TtsStreamProperties streamProperties) {
        this.objectMapper = objectMapper;
        this.ttsProperties = ttsProperties;
        this.streamProperties = streamProperties;
    }

    @PostConstruct
    public void init() {
        CartesiaTtsProperties cartesia = ttsProperties.cartesia();
        if (cartesia == null || cartesia.apiKey() == null || cartesia.apiKey().isBlank()) {
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(cartesia.connectTimeoutSeconds()))
                .build();
        if (streamProperties.cartesiaWebSocket()) {
            webSocketClient = new CartesiaWebSocketClient(client, objectMapper, cartesia, API_VERSION);
        }
        log.info("Cartesia Sonic TTS ready (url={}, model={}, rate={} Hz, transport={})",
                cartesia.url(), cartesia.model(), cartesia.sampleRate(),
                webSocketClient != null ? "websocket" : "bytes");
    }

    @PreDestroy
    public void shutdown() {
        CartesiaWebSocketClient current = webSocketClient;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public String name() {
        return "cartesia";
    }

    /** Sonic speaks 44 languages (Russian among them); Uzbek is not one of them. */
    @Override
    public boolean supports(String language) {
        return language == null || !language.toLowerCase(Locale.ROOT).startsWith("uz");
    }

    @Override
    public short[] synthesize(String text, String language, String voice) {
        return synthesize(text, language, voice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
        List<short[]> chunks = new ArrayList<>();
        synthesizeStreaming(text, language, voice, style, chunks::add);

        int total = 0;
        for (short[] chunk : chunks) {
            total += chunk.length;
        }
        short[] pcm = new short[total];
        int offset = 0;
        for (short[] chunk : chunks) {
            System.arraycopy(chunk, 0, pcm, offset, chunk.length);
            offset += chunk.length;
        }
        return pcm;
    }

    @Override
    public void synthesizeStreaming(String text, String language, String voice, EffectiveVoiceSettings style,
                                    PcmChunkListener onChunk) {
        CartesiaTtsProperties cartesia = ttsProperties.cartesia();
        HttpClient current = client;
        if (current == null || cartesia == null || cartesia.apiKey() == null || cartesia.apiKey().isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_CLIENT_UNAVAILABLE, "cartesia");
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model_id", cartesia.model());
            root.put("transcript", text);

            String voiceId = (voice != null && !voice.isBlank()) ? voice : cartesia.voice();
            if (voiceId != null && !voiceId.isBlank()) {
                root.putObject("voice").put("id", voiceId);
            }

            if (language != null && !language.isBlank()) {
                root.put("language", language.length() >= 2 ? language.substring(0, 2) : language);
            }

            ObjectNode output = root.putObject("output_format");
            output.put("container", "raw");
            output.put("encoding", "pcm_s16le");
            output.put("sample_rate", cartesia.sampleRate());

            CartesiaWebSocketClient socketClient = webSocketClient;
            if (socketClient != null) {
                socketClient.generate(UUID.randomUUID().toString(), root, onChunk);
                return;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cartesia.url()))
                    .header("Authorization", "Bearer " + cartesia.apiKey())
                    .header("Cartesia-Version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .timeout(Duration.ofSeconds(cartesia.connectTimeoutSeconds()))
                    .build();

            HttpResponse<InputStream> response = current.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                // The vendor's body is for the log only; the caller gets the status.
                try (InputStream body = response.body()) {
                    log.warn("Cartesia TTS answered HTTP {}: {}", response.statusCode(),
                            new String(body.readNBytes(ERROR_BODY_LOG_BYTES), StandardCharsets.UTF_8));
                }
                throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_HTTP_STATUS, "cartesia",
                        response.statusCode());
            }
            streamPcm(response.body(), onChunk);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, e.getMessage());
        }
    }

    /**
     * Hand the body on as it arrives. A read can stop halfway through a 16-bit sample, so an
     * odd trailing byte is carried into the next read.
     */
    private void streamPcm(InputStream body, PcmChunkListener onChunk) throws Exception {
        try (InputStream in = body) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            byte carry = 0;
            boolean carried = false;
            int read;
            while ((read = in.read(buffer)) > 0) {
                short[] samples = new short[(read + (carried ? 1 : 0)) / 2];
                int index = 0;
                int cursor = 0;
                if (carried) {
                    samples[index++] = (short) ((buffer[cursor++] << 8) | (carry & 0xFF));
                    carried = false;
                }
                while (read - cursor >= 2) {
                    samples[index++] = (short) ((buffer[cursor + 1] << 8) | (buffer[cursor] & 0xFF));
                    cursor += 2;
                }
                if (read - cursor == 1) {
                    carry = buffer[cursor];
                    carried = true;
                }
                if (index > 0 && onChunk != null) {
                    onChunk.onChunk(Arrays.copyOf(samples, index));
                }
            }
        }
    }

    /**
     * The account's own voices ({@code GET /voices?is_owner=true}) — not the public library,
     * whose hundreds of voices would bury the picker — each offered for its one native language. Asked with the current API version, which
     * pages as {@code {data, has_more, next_page}}; older versions answered this route with
     * a bare array, so that shape is read too.
     */
    @Override
    public List<ProviderVoice> listVoices() {
        CartesiaTtsProperties cartesia = ttsProperties.cartesia();
        HttpClient current = client;
        if (current == null || cartesia == null || cartesia.apiKey() == null || cartesia.apiKey().isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_CLIENT_UNAVAILABLE, "cartesia");
        }
        URI voicesUri = URI.create(cartesia.url()).resolve("/voices?limit=100&is_owner=true");
        List<ProviderVoice> voices = new ArrayList<>();
        String after = null;
        try {
            do {
                URI page = after == null ? voicesUri
                        : URI.create(voicesUri + "&starting_after=" + URLEncoder.encode(after, StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(page)
                        .header("Authorization", "Bearer " + cartesia.apiKey())
                        .header("Cartesia-Version", API_VERSION)
                        .timeout(Duration.ofSeconds(cartesia.connectTimeoutSeconds()))
                        .GET()
                        .build();
                HttpResponse<String> response = current.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_HTTP_STATUS, "cartesia", response.statusCode());
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.isArray() ? root : root.path("data");
                for (JsonNode voice : data) {
                    String id = voice.path("id").asText("");
                    String language = voice.path("language").asText("");
                    if (!id.isBlank() && !language.isBlank()) {
                        voices.add(new ProviderVoice(id, voiceLabel(voice, id),
                                Set.of(language.split("-")[0].toLowerCase(Locale.ROOT)),
                                voice.path("gender").asText(null), null));
                    }
                }
                after = !root.isArray() && root.path("has_more").asBoolean(false)
                        ? root.path("next_page").asText("") : "";
            } while (!after.isBlank());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_VOICES_FAILED, "cartesia", e, e.getMessage());
        }
        return voices;
    }

    /** "Katie (feminine)" — the vendor's name and gender. */
    private static String voiceLabel(JsonNode voice, String id) {
        String name = voice.path("name").asText("");
        String gender = voice.path("gender").asText("");
        return (name.isBlank() ? id : name) + (gender.isBlank() ? "" : " (" + gender + ")");
    }
}
