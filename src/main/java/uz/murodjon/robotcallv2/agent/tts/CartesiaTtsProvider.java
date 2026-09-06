package uz.murodjon.robotcallv2.agent.tts;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;

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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TtsProperties ttsProperties;
    private volatile HttpClient client;

    public CartesiaTtsProvider(TtsProperties ttsProperties) {
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
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "cartesia", "not configured");
        }

        try {
            ObjectNode root = MAPPER.createObjectNode();
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
                throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "cartesia",
                        "HTTP " + response.statusCode() + ": " + new String(response.body()));
            }

            byte[] pcmBytes = response.body();
            short[] pcm = new short[pcmBytes.length / 2];
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            return pcm;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_AISHA_SYNTH_FAILED, "cartesia", e, e.getMessage());
        }
    }
}
