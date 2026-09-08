package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.StreamingDownsampler;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * OpenAI Text-to-Speech provider ({@code POST /v1/audio/speech}).
 *
 * <p>Asks for {@code response_format: "pcm"} — raw 24 kHz 16-bit little-endian mono, no
 * header — and streams the chunked response straight into the 24k→8k decimator, so the
 * first audio reaches the caller while the rest is still being synthesized. That format
 * is also the one OpenAI documents as fastest to first byte, which is the number this
 * pipeline is built around (§1.3).
 */
@Component
@ConditionalOnExpression("!'${voice-agent.tts.open-ai.api-key:}'.isBlank() || !'${OPENAI_API_KEY:}'.isBlank()")
public class OpenAiTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Voices the speech endpoint accepts. Checked rather than passed through: an unknown
     * name is a 400 from OpenAI mid-call, and falling back to the configured voice keeps
     * the caller hearing something.
     */
    private static final Set<String> KNOWN_VOICES = Set.of(
            "alloy", "ash", "ballad", "coral", "echo", "fable",
            "nova", "onyx", "sage", "shimmer", "verse", "marin", "cedar");

    /** The four the older tts-1 models do not have. */
    private static final Set<String> GPT_ONLY_VOICES = Set.of("ballad", "verse", "marin", "cedar");

    /** How much of the chunked body to take at a time — about 40 ms of 24 kHz audio. */
    private static final int READ_BUFFER_BYTES = 2048;

    private final TtsProperties ttsProperties;
    private volatile HttpClient client;

    public OpenAiTtsProvider(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    @PostConstruct
    public void init() {
        if (resolveApiKey().isBlank()) {
            log.warn("OpenAI TTS selected but no API key is available (voice-agent.tts.open-ai.api-key or OPENAI_API_KEY)");
            return;
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings().connectTimeoutSeconds()))
                .build();
        log.info("OpenAI TTS provider ready (model={}, defaultVoice={}, rate={}Hz)",
                settings().model(), settings().voice(), settings().sampleRate());
    }

    @Override
    public String name() {
        return "openai";
    }

    /**
     * Every language, because the endpoint takes no language parameter at all — it speaks
     * whatever the text is written in. Whether its Uzbek is good enough for a debt call is
     * a judgement for whoever picks it, not something this can answer.
     */
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
            throw new ExternalServiceException(ErrorCode.TTS_OPENAI_CLIENT_UNAVAILABLE, "openai-tts");
        }

        OpenAiTtsProperties settings = settings();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.url()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody(text, resolveVoice(language, requestedVoice), settings)))
                .timeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();

        try {
            HttpResponse<InputStream> response = current.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new ExternalServiceException(ErrorCode.TTS_OPENAI_SYNTH_FAILED, "openai-tts",
                        "HTTP " + response.statusCode());
            }
            streamPcm(response.body(), settings.sampleRate(), onChunk);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.TTS_OPENAI_SYNTH_FAILED, "openai-tts", e, e.getMessage());
        }
    }

    /**
     * Decimate the chunked body to 8 kHz as it arrives.
     *
     * <p>One filter for the whole utterance, for the reason {@code GeminiTtsProvider}
     * documents: resampling each chunk on its own leaves a discontinuity at every
     * boundary. A read can also stop halfway through a 16-bit sample, so an odd trailing
     * byte is carried into the next read rather than being decoded as a sample of its own.
     */
    private static void streamPcm(InputStream body, int sourceRate, PcmChunkListener onChunk) throws Exception {
        StreamingDownsampler downsampler = sourceRate == 16000
                ? StreamingDownsampler.from16kTo8k()
                : StreamingDownsampler.from24kTo8k();
        try (InputStream in = body) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            byte carry = 0;
            boolean carried = false;
            int read;
            while ((read = in.read(buffer)) > 0) {
                int available = read + (carried ? 1 : 0);
                short[] samples = new short[available / 2];
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
                if (index == 0) {
                    continue;
                }
                short[] pcm8k = sourceRate == 8000
                        ? java.util.Arrays.copyOf(samples, index)
                        : downsampler.push(samples, index);
                if (pcm8k.length > 0 && onChunk != null) {
                    onChunk.onChunk(pcm8k);
                }
            }
        }
        short[] tail = sourceRate == 8000 ? new short[0] : downsampler.flush();
        if (tail.length > 0 && onChunk != null) {
            onChunk.onChunk(tail);
        }
    }

    private static String requestBody(String text, String voice, OpenAiTtsProperties settings) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", settings.model());
        root.put("input", text);
        root.put("voice", voice);
        root.put("response_format", "pcm");
        if (settings.instructions() != null && !settings.instructions().isBlank()) {
            root.put("instructions", settings.instructions().trim());
        }
        return root.toString();
    }

    /**
     * The campaign's voice if the endpoint has one by that name, otherwise the configured
     * one for this language. An unknown name is logged rather than sent: it would come
     * back a 400 while the caller waits.
     */
    private String resolveVoice(String language, String requested) {
        OpenAiTtsProperties settings = settings();
        if (requested != null && !requested.isBlank()) {
            String candidate = requested.trim().toLowerCase(Locale.ROOT);
            if (KNOWN_VOICES.contains(candidate) && isAvailableOn(candidate, settings.model())) {
                return candidate;
            }
            log.warn("OpenAI TTS voice '{}' is not one this endpoint speaks — using {}",
                    requested, settings.voice());
        }
        Map<String, String> perLanguage = settings.voices();
        if (language != null && perLanguage.containsKey(language)) {
            return perLanguage.get(language);
        }
        return settings.voice();
    }

    /** The four newer voices exist only on the gpt-* models, not on tts-1 / tts-1-hd. */
    private static boolean isAvailableOn(String voice, String model) {
        return !GPT_ONLY_VOICES.contains(voice) || model.startsWith("gpt-");
    }

    private OpenAiTtsProperties settings() {
        OpenAiTtsProperties configured = ttsProperties.openAi();
        return configured != null ? configured : new OpenAiTtsProperties(null, null, null, null, null, null, 0, 0);
    }

    private String resolveApiKey() {
        OpenAiTtsProperties settings = ttsProperties.openAi();
        if (settings != null && settings.apiKey() != null && !settings.apiKey().isBlank()) {
            return settings.apiKey().trim();
        }
        String env = System.getenv("OPENAI_API_KEY");
        return env != null ? env.trim() : "";
    }
}
