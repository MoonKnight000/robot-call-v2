package uz.murodjon.uysotvoice.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Text-to-speech settings. Bound from {@code voice-agent.tts.*} (PROJECT.md §12).
 *
 * @param enabled         master switch; when false no TTS is synthesized
 * @param provider        preferred provider id ({@code google}/{@code yandex}); used
 *                        first when it supports the language, else the router falls
 *                        back by language support
 * @param defaultLanguage BCP-47 language used when a call/turn does not specify one
 * @param google          Google TTS settings (uz-UZ and ru-RU)
 * @param yandex          Yandex SpeechKit settings (uz-UZ via the Nigora voice, ru-RU)
 */
@ConfigurationProperties(prefix = "voice-agent.tts")
public record TtsProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        Google google,
        Yandex yandex
) {

    /**
     * @param enabled      whether the Google provider bean is created
     * @param sampleRate   output sample rate (8000 = telephone; matches RTP path)
     * @param speakingRate 0.25–4.0, 1.0 = normal
     * @param pitch        semitone offset, 0.0 = default
     * @param voices       optional per-language voice names (e.g. {@code uz-UZ -> uz-UZ-Standard-A});
     *                     blank/absent lets Google pick a default voice for the language
     */
    public record Google(
            boolean enabled,
            int sampleRate,
            double speakingRate,
            double pitch,
            Map<String, String> voices
    ) {
    }

    /**
     * @param enabled    whether the Yandex provider bean is created (needs an API key)
     * @param apiKey     SpeechKit API key (sent as {@code Authorization: Api-Key ...})
     * @param folderId   optional Yandex Cloud folder id (only needed for some auth setups)
     * @param voice      default voice name (ru-RU: alena, filipp, ermil, jane, omazh, zahar, ...)
     * @param voices     per-language voice overrides (e.g. {@code uz-UZ -> nigora}); a
     *                   language absent here falls back to {@code voice}. Each language
     *                   needs its own voice — a ru-RU voice cannot speak Uzbek text.
     * @param emotion    voice emotion/role: neutral | good | evil
     * @param apiUrl     REST synthesize endpoint
     * @param sampleRate LPCM output rate (8000 for telephone)
     */
    public record Yandex(
            boolean enabled,
            String apiKey,
            String folderId,
            String voice,
            Map<String, String> voices,
            String emotion,
            String apiUrl,
            int sampleRate
    ) {
    }
}
