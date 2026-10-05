package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cartesia Sonic Ultra-Fast TTS Provider (<90ms TTFB).
 *
 * <p>Synthesizes direct 8 kHz LINEAR16 PCM telephony audio over HTTP/2 streaming bytes,
 * bypassing heavy container parsing for near-instant turnaround.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.cartesia.api-key:}'.isBlank()")
public class CartesiaTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(CartesiaTtsProvider.class);
    private final ObjectMapper objectMapper;

    private final TtsProperties ttsProperties;
    private volatile HttpClient client;

    public CartesiaTtsProvider(TtsProperties ttsProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ttsProperties = ttsProperties;
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
        log.info("Cartesia Sonic TTS ready (url={}, model={}, rate={} Hz)",
                cartesia.url(), cartesia.model(), cartesia.sampleRate());
    }

    @Override
    public String name() {
        return "cartesia";
    }

    @Override
    public boolean supports(String language) {
        return true;
    }

    @Override
    public short[] synthesize(String text, String language, String voice) {
        return synthesize(text, language, voice, EffectiveVoiceSettings.NONE);
    }

    @Override
    public short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
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
                root.putObject("voice").put("mode", "id").put("id", voiceId);
            }

            if (language != null && !language.isBlank()) {
                root.put("language", language.length() >= 2 ? language.substring(0, 2) : language);
            }

            ObjectNode output = root.putObject("output_format");
            output.put("container", "raw");
            output.put("encoding", "pcm_s16le");
            output.put("sample_rate", cartesia.sampleRate());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cartesia.url()))
                    .header("X-API-Key", cartesia.apiKey())
                    .header("Cartesia-Version", "2024-06-10")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .timeout(Duration.ofSeconds(cartesia.connectTimeoutSeconds()))
                    .build();

            HttpResponse<byte[]> response = current.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                // The vendor's body is for the log only; the caller gets the status.
                log.warn("Cartesia TTS answered HTTP {}: {}", response.statusCode(), new String(response.body()));
                throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_HTTP_STATUS, "cartesia",
                        response.statusCode());
            }

            byte[] pcmBytes = response.body();
            short[] pcm = new short[pcmBytes.length / 2];
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            return pcm;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_CARTESIA_SYNTH_FAILED, "cartesia", e, e.getMessage());
        }
    }

    /**
     * The account's own voices ({@code GET /voices?is_owner=true}) — not the public library,
     * whose hundreds of voices would bury the picker — each offered for its one native language. Asked with the current API version, which
     * pages as {@code {data, has_more, next_page}}; the 2024-06-10 synthesis above answered
     * this route with a bare array, so that shape is read too.
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
                        .header("Cartesia-Version", "2026-08-14")
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
                                Set.of(language.split("-")[0].toLowerCase(Locale.ROOT))));
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
