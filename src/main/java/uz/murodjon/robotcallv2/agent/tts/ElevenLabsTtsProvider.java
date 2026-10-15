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

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * ElevenLabs Text-to-Speech provider ({@code POST /v1/text-to-speech/{voice_id}/stream}).
 *
 * <p>Asks for {@code output_format=pcm_8000} — headerless 8 kHz 16-bit little-endian mono —
 * so the chunked body is already telephone audio and goes to the caller without a
 * resampler. The default model is {@code eleven_v4_turbo}: the v4 family is the first to
 * speak Uzbek, and Turbo is its realtime variant.
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.eleven-labs.api-key:}'.isBlank() || !'${ELEVENLABS_API_KEY:}'.isBlank()")
public class ElevenLabsTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTtsProvider.class);
    private final ObjectMapper objectMapper;

    /** About 40 ms of 8 kHz audio per read. */
    private static final int READ_BUFFER_BYTES = 640;

    private final TtsProperties ttsProperties;
    private volatile HttpClient client;

    public ElevenLabsTtsProvider(TtsProperties ttsProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ttsProperties = ttsProperties;
    }

    @PostConstruct
    public void init() {
        if (resolveApiKey().isBlank()) {
            log.warn("ElevenLabs TTS selected but no API key is available (voice-agent.tts.eleven-labs.api-key or ELEVENLABS_API_KEY)");
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings().connectTimeoutSeconds()))
                .build();
        log.info("ElevenLabs TTS provider ready (model={}, defaultVoice={})", settings().model(), settings().voice());
    }

    @Override
    public String name() {
        return "elevenlabs";
    }

    /** Every language: v4 speaks 90+ of them, and the language code is passed per request. */
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
    public void synthesizeStreaming(String text, String language, String requestedVoice,
                                    EffectiveVoiceSettings style, PcmChunkListener onChunk) {
        if (text == null || text.isBlank()) {
            return;
        }
        HttpClient current = client;
        String apiKey = resolveApiKey();
        if (current == null || apiKey.isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_CLIENT_UNAVAILABLE, "elevenlabs-tts");
        }
        String voice = resolveVoice(language, requestedVoice);
        if (voice == null) {
            throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_VOICE_NOT_CONFIGURED, "elevenlabs-tts", language);
        }

        ElevenLabsTtsProperties settings = settings();
        String base = settings.url().endsWith("/") ? settings.url().substring(0, settings.url().length() - 1) : settings.url();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/" + voice + "/stream?output_format=pcm_8000"))
                .header("Content-Type", "application/json")
                .header("xi-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(text, language, settings)))
                .timeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();

        try {
            HttpResponse<InputStream> response = current.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_HTTP_STATUS, "elevenlabs-tts",
                        response.statusCode());
            }
            streamPcm(response.body(), onChunk);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_SYNTH_FAILED, "elevenlabs-tts", e, e.getMessage());
        }
    }

    /**
     * The account's voices ({@code GET /v2/voices}): the premade ones plus whatever was added
     * from the Voice Library. Every one is offered for every language — v4 speaks 90+ of them
     * with any voice and puts the accent on by itself — so {@code languages} is left empty.
     * Cloned voices are left out: each belongs to the one company that made it
     * (VoiceCloneService), and listing it here would file it as a voice for everyone.
     */
    @Override
    public List<ProviderVoice> listVoices() {
        HttpClient current = client;
        String apiKey = resolveApiKey();
        if (current == null || apiKey.isBlank()) {
            throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_CLIENT_UNAVAILABLE, "elevenlabs-tts");
        }
        URI voicesUri = URI.create(settings().url()).resolve("/v2/voices?page_size=100");
        List<ProviderVoice> voices = new ArrayList<>();
        String pageToken = null;
        try {
            do {
                URI page = pageToken == null ? voicesUri
                        : URI.create(voicesUri + "&next_page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(page)
                        .header("xi-api-key", apiKey)
                        .timeout(Duration.ofSeconds(settings().connectTimeoutSeconds()))
                        .GET()
                        .build();
                HttpResponse<String> response = current.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_HTTP_STATUS, "elevenlabs-tts",
                            response.statusCode());
                }
                JsonNode root = objectMapper.readTree(response.body());
                for (JsonNode voice : root.path("voices")) {
                    String id = voice.path("voice_id").asText("");
                    if (!id.isBlank() && !"cloned".equals(voice.path("category").asText(""))) {
                        voices.add(new ProviderVoice(id, label(voice), Set.of()));
                    }
                }
                pageToken = root.path("has_more").asBoolean(false) ? root.path("next_page_token").asText(null) : null;
            } while (pageToken != null && !pageToken.isBlank());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            String detail = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : (e.getCause() != null && e.getCause().getMessage() != null)
                            ? e.getCause().getMessage()
                            : e.getClass().getSimpleName();
            throw new ExternalServiceException(ErrorCode.TTS_ELEVENLABS_VOICES_FAILED, "elevenlabs-tts", e, detail);
        }
        return voices;
    }

    /** "Rachel (female, american)" — the vendor's own name and descriptors. */
    private static String label(JsonNode voice) {
        String name = voice.path("name").asText("");
        List<String> traits = new ArrayList<>();
        for (String key : List.of("gender", "accent")) {
            String value = voice.path("labels").path(key).asText("");
            if (!value.isBlank()) {
                traits.add(value);
            }
        }
        return traits.isEmpty() ? name : name + " (" + String.join(", ", traits) + ")";
    }

    /**
     * Hand the chunked body on as it arrives. A read can stop halfway through a 16-bit
     * sample, so an odd trailing byte is carried into the next read.
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

    /** {@code language_code} is ISO 639-1, so {@code uz-UZ} goes out as {@code uz}. */
    private String requestBody(String text, String language, ElevenLabsTtsProperties settings) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        root.put("model_id", settings.model());
        if (language != null && !language.isBlank()) {
            root.put("language_code", language.split("-")[0].toLowerCase(Locale.ROOT));
        }
        return root.toString();
    }

    /**
     * The campaign's voice id as given, otherwise the configured one for this language,
     * otherwise the default. Null when none is set — ElevenLabs has no voice to fall back
     * on without an id, and a guessed one may not exist on this account.
     */
    private String resolveVoice(String language, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        ElevenLabsTtsProperties settings = settings();
        String perLanguage = language != null ? settings.voices().get(language) : null;
        if (perLanguage != null && !perLanguage.isBlank()) {
            return perLanguage.trim();
        }
        return settings.voice() != null && !settings.voice().isBlank() ? settings.voice().trim() : null;
    }

    private ElevenLabsTtsProperties settings() {
        ElevenLabsTtsProperties configured = ttsProperties.elevenLabs();
        return configured != null ? configured : new ElevenLabsTtsProperties(null, null, null, null, null, 0);
    }

    private String resolveApiKey() {
        ElevenLabsTtsProperties settings = ttsProperties.elevenLabs();
        if (settings != null && settings.apiKey() != null && !settings.apiKey().isBlank()) {
            return settings.apiKey().trim();
        }
        String env = System.getenv("ELEVENLABS_API_KEY");
        return env != null ? env.trim() : "";
    }
}
