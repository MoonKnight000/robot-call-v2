package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.StreamingDownsampler;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Gemini Text-to-Speech provider ({@code gemini-3.1-flash-tts-preview}).
 *
 * <p>Synthesizes expressive, controllable speech streaming chunk-by-chunk via SSE
 * ({@code streamGenerateContent?alt=sse}) and resamples audio to 8 kHz mono linear PCM
 * for real-time telephone playback.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.gemini.api-key:}'.isBlank() || !'${GEMINI_API_KEY:}'.isBlank()")
public class GeminiTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiTtsProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_MODEL = "gemini-3.1-flash-tts-preview";
    private static final String DEFAULT_VOICE = "Aoede";
    private static final int DEFAULT_SAMPLE_RATE = 24000;

    private static final Map<String, String> KNOWN_VOICES = Map.of(
            "aoede", "Aoede",
            "kore", "Kore",
            "puck", "Puck",
            "charon", "Charon",
            "fenrir", "Fenrir"
    );

    private final TtsProperties ttsProperties;
    private volatile HttpClient client;

    public GeminiTtsProvider(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    @PostConstruct
    public void init() {
        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            log.warn("Gemini TTS selected but no API key is available (voice-agent.tts.gemini.api-key or GEMINI_API_KEY)");
            return;
        }
        int timeoutSeconds = ttsProperties.gemini() != null ? ttsProperties.gemini().connectTimeoutSeconds() : 10;
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        log.info("Gemini TTS provider ready (model={}, defaultVoice={}, rate={}Hz)",
                resolveModel(), resolveDefaultVoice(), resolveSampleRate());
    }

    @Override
    public String name() {
        return "gemini";
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
        List<short[]> chunks = new ArrayList<>();
        synthesizeStreaming(text, language, voice, style, chunks::add);

        int totalLen = 0;
        for (short[] chunk : chunks) {
            totalLen += chunk.length;
        }
        short[] fullPcm = new short[totalLen];
        int offset = 0;
        for (short[] chunk : chunks) {
            System.arraycopy(chunk, 0, fullPcm, offset, chunk.length);
            offset += chunk.length;
        }
        return fullPcm;
    }

    @Override
    public void synthesizeStreaming(String text, String language, String requestedVoice,
                                   EffectiveVoiceSettings style, PcmChunkListener onChunk) {
        if (text == null || text.isBlank()) {
            return;
        }

        HttpClient current = client;
        if (current == null) {
            throw new ExternalServiceException(ErrorCode.TTS_GEMINI_CLIENT_UNAVAILABLE, "gemini-tts");
        }

        String apiKey = resolveApiKey();
        if (apiKey.isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_GEMINI_CLIENT_UNAVAILABLE, "gemini-tts");
        }

        String voiceName = resolveVoice(language, requestedVoice);
        String endpoint = buildEndpointUri(apiKey);
        String requestBody = buildRequestBody(text, voiceName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(ttsProperties.gemini() != null ? ttsProperties.gemini().connectTimeoutSeconds() : 10))
                .build();

        try {
            HttpResponse<Stream<String>> response = current.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                throw new ExternalServiceException(ErrorCode.TTS_GEMINI_SYNTH_FAILED, "gemini-tts",
                        "HTTP " + response.statusCode());
            }

            int sourceRate = resolveSampleRate();
            // One filter for the whole utterance: the chunks are 40 ms each, and resampling
            // them one by one put a click at every boundary — twenty-five a second.
            StreamingDownsampler downsampler = sourceRate == 16000
                    ? StreamingDownsampler.from16kTo8k()
                    : StreamingDownsampler.from24kTo8k();
            // A chunk may end halfway through a 16-bit sample; the odd byte belongs to the next one.
            byte[] carry = new byte[1];
            boolean[] carried = {false};
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    if (line == null || !line.contains("data:")) {
                        return;
                    }
                    String json = line.substring(line.indexOf("data:") + 5).trim();
                    if (json.isEmpty()) {
                        return;
                    }
                    try {
                        JsonNode root = MAPPER.readTree(json);
                        JsonNode candidates = root.path("candidates");
                        if (!candidates.isArray() || candidates.isEmpty()) {
                            return;
                        }
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (!parts.isArray()) {
                            return;
                        }
                        for (JsonNode part : parts) {
                            JsonNode inline = part.path("inlineData");
                            if (inline.isMissingNode() || !inline.hasNonNull("data")) {
                                continue;
                            }
                            byte[] audioData = Base64.getDecoder().decode(inline.get("data").asText());
                            if (carried[0]) {
                                byte[] joined = new byte[audioData.length + 1];
                                joined[0] = carry[0];
                                System.arraycopy(audioData, 0, joined, 1, audioData.length);
                                audioData = joined;
                                carried[0] = false;
                            }
                            if (audioData.length % 2 == 1 && !startsWithRiff(audioData)) {
                                carry[0] = audioData[audioData.length - 1];
                                carried[0] = true;
                            }
                            short[] source = toSourceSamples(audioData);
                            short[] pcm8k = sourceRate == 8000 ? source : downsampler.push(source, source.length);
                            if (pcm8k.length > 0 && onChunk != null) {
                                onChunk.onChunk(pcm8k);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Gemini TTS chunk: {}", e.getMessage());
                    }
                });
            }
            short[] tail = sourceRate == 8000 ? new short[0] : downsampler.flush();
            if (tail.length > 0 && onChunk != null) {
                onChunk.onChunk(tail);
            }
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_GEMINI_SYNTH_FAILED, "gemini-tts", e, e.getMessage());
        }
    }

    private String resolveApiKey() {
        if (ttsProperties.gemini() != null && ttsProperties.gemini().apiKey() != null && !ttsProperties.gemini().apiKey().isBlank()) {
            return ttsProperties.gemini().apiKey().trim();
        }
        String env = System.getenv("GEMINI_API_KEY");
        return env != null ? env.trim() : "";
    }

    private String resolveModel() {
        if (ttsProperties.gemini() != null && ttsProperties.gemini().model() != null && !ttsProperties.gemini().model().isBlank()) {
            String m = ttsProperties.gemini().model().trim();
            return m.startsWith("models/") ? m.substring("models/".length()) : m;
        }
        return DEFAULT_MODEL;
    }

    private String resolveDefaultVoice() {
        if (ttsProperties.gemini() != null && ttsProperties.gemini().voice() != null && !ttsProperties.gemini().voice().isBlank()) {
            return ttsProperties.gemini().voice().trim();
        }
        return DEFAULT_VOICE;
    }

    private int resolveSampleRate() {
        return (ttsProperties.gemini() != null && ttsProperties.gemini().sampleRate() > 0)
                ? ttsProperties.gemini().sampleRate()
                : DEFAULT_SAMPLE_RATE;
    }

    private String resolveVoice(String language, String requestedVoice) {
        if (requestedVoice != null && !requestedVoice.isBlank()) {
            String trimmed = requestedVoice.trim();
            String matched = KNOWN_VOICES.get(trimmed.toLowerCase());
            return matched != null ? matched : trimmed;
        }
        if (ttsProperties.gemini() != null && ttsProperties.gemini().voices() != null && language != null) {
            String v = ttsProperties.gemini().voices().get(language);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return resolveDefaultVoice();
    }

    private String buildEndpointUri(String apiKey) {
        String base = (ttsProperties.gemini() != null && ttsProperties.gemini().url() != null && !ttsProperties.gemini().url().isBlank())
                ? ttsProperties.gemini().url().trim()
                : "https://generativelanguage.googleapis.com/v1beta/models";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + resolveModel() + ":streamGenerateContent?alt=sse&key=" + apiKey;
    }

    private String buildRequestBody(String text, String voiceName) {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", text);

        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.putArray("responseModalities").add("AUDIO");
        genConfig.putObject("speechConfig")
                .putObject("voiceConfig")
                .putObject("prebuiltVoiceConfig")
                .put("voiceName", voiceName);

        return root.toString();
    }

    /**
     * The samples in one chunk at the source rate: a WAV container's payload, or raw
     * little-endian s16 PCM. A trailing odd byte is ignored — the caller carries it over.
     */
    static short[] toSourceSamples(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new short[0];
        }
        if (startsWithRiff(audioData)) {
            try {
                WavAudio wav = WavReader.read(audioData);
                return wav.samples();
            } catch (Exception e) {
                log.debug("WAV parsing skipped, interpreting as raw PCM: {}", e.getMessage());
            }
        }
        short[] pcm = new short[audioData.length / 2];
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        return pcm;
    }

    private static boolean startsWithRiff(byte[] audioData) {
        return audioData.length >= 12 && audioData[0] == 'R' && audioData[1] == 'I'
                && audioData[2] == 'F' && audioData[3] == 'F';
    }
}
