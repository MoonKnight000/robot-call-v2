package uz.murodjon.uysotvoice.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Yandex SpeechKit text-to-speech for {@code ru-RU} and {@code uz-UZ} (PROJECT.md §2.5).
 * The voice is per-language ({@code voice-agent.tts.yandex.voices}); Uzbek uses the
 * Nigora voice, Russian the default {@code voice}.
 *
 * <p>Uses the REST {@code v1 tts:synthesize} endpoint with {@code format=lpcm},
 * which returns headerless signed 16-bit little-endian PCM at the requested rate
 * — a perfect match for the RTP path, and it avoids generating gRPC stubs
 * (§2.3). Auth is an API key sent as {@code Authorization: Api-Key <key>}.
 * Created only when {@code voice-agent.tts.yandex.enabled=true} and a key is set.
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "voice-agent.tts.yandex", name = "enabled", havingValue = "true")
public class YandexTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(YandexTtsProvider.class);

    private final TtsProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public YandexTtsProvider(TtsProperties props) {
        this.props = props;
        if (props.yandex().apiKey() == null || props.yandex().apiKey().isBlank()) {
            log.warn("Yandex TTS enabled but voice-agent.tts.yandex.api-key is blank — synthesis will fail");
        } else {
            log.info("Yandex TTS ready (voice='{}', sampleRate={})",
                    props.yandex().voice(), props.yandex().sampleRate());
        }
    }

    @Override
    public String name() {
        return "yandex";
    }

    @Override
    public boolean supports(String language) {
        // SpeechKit speaks both Uzbek (uz-UZ, Nigora) and Russian — but only with a
        // voice for that language, so require one to be configured.
        return language != null
                && (language.startsWith("ru") || language.startsWith("uz"))
                && !voiceFor(language).isBlank();
    }

    @Override
    public short[] synthesize(String text, String language) {
        TtsProperties.Yandex y = props.yandex();
        StringBuilder form = new StringBuilder()
                .append("text=").append(enc(text))
                .append("&lang=").append(enc(language))
                .append("&voice=").append(enc(voiceFor(language)))
                .append("&emotion=").append(enc(y.emotion()))
                .append("&format=lpcm")
                .append("&sampleRateHertz=").append(y.sampleRate());
        if (y.folderId() != null && !y.folderId().isBlank()) {
            form.append("&folderId=").append(enc(y.folderId()));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(y.apiUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Api-Key " + y.apiKey())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Yandex TTS HTTP " + response.statusCode() + ": "
                        + new String(response.body(), StandardCharsets.UTF_8));
            }
            return toPcm16(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Yandex TTS request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Voice for the requested language: the {@code voices} override for the exact tag,
     * then for its language prefix. The default {@code voice} is a Russian one, so it
     * is only used for ru-RU — other languages need an explicit entry (empty = the
     * language is not supported).
     */
    private String voiceFor(String language) {
        if (language == null) {
            return "";
        }
        Map<String, String> voices = props.yandex().voices();
        if (voices != null && !voices.isEmpty()) {
            String exact = voices.get(language);
            if (exact != null && !exact.isBlank()) {
                return exact;
            }
            String prefix = language.substring(0, Math.min(2, language.length())).toLowerCase();
            for (Map.Entry<String, String> e : voices.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase().startsWith(prefix)
                        && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        String fallback = props.yandex().voice();
        return language.startsWith("ru") && fallback != null ? fallback : "";
    }

    /** Decode headerless little-endian LPCM bytes into 16-bit samples. */
    private static short[] toPcm16(byte[] lpcm) {
        short[] pcm = new short[lpcm.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            int lo = lpcm[i * 2] & 0xFF;
            int hi = lpcm[i * 2 + 1];
            pcm[i] = (short) ((hi << 8) | lo);
        }
        return pcm;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
