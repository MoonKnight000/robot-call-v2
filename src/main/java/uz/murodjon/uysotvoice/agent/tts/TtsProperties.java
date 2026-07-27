package uz.murodjon.uysotvoice.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Text-to-speech settings. Bound from {@code voice-agent.tts.*} (PROJECT.md §12).
 *
 * @param enabled         master switch; when false no TTS is synthesized
 * @param provider        preferred provider id ({@code google}/{@code yandex}); used
 *                        first when it supports the language, else the router falls
 *                        back by language support
 * @param defaultLanguage BCP-47 language used when a call/turn does not specify one
 * @param cache           synthesized-audio cache settings; a hit removes both a
 *                        synthesis round trip from a live turn and a per-character
 *                        charge from the bill
 * @param catalog         voices a campaign may be created with; empty means the
 *                        configured routing above is the only option
 * @param google          Google TTS settings (uz-UZ and ru-RU)
 * @param yandex          Yandex SpeechKit settings (uz-UZ via the Nigora voice, ru-RU)
 */
@ConfigurationProperties(prefix = "voice-agent.tts")
public record TtsProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        Cache cache,
        List<Voice> catalog,
        Google google,
        Yandex yandex
) {

    /**
     * One selectable voice (PROJECT.md §2.5). A campaign stores {@link #id()}; the
     * catalog is what turns it back into a provider plus a provider-side voice name,
     * so the choice pins both — a voice is only ever spoken by the engine that owns it.
     *
     * @param id       stable id stored on the campaign (e.g. {@code nigora})
     * @param provider provider that owns the voice ({@code yandex}/{@code google})
     * @param language BCP-47 language the voice speaks; a call in another language
     *                 ignores it and falls back to normal routing, because a Russian
     *                 voice reading Uzbek text is worse than the default voice
     * @param name     provider-side voice name sent with the synthesis request
     * @param label    human-readable name shown in the campaign UI
     */
    public record Voice(
            String id,
            String provider,
            String language,
            String name,
            String label
    ) {
    }

    /**
     * @param size     how many lines to keep in the per-process LRU; {@code 0} disables it
     * @param maxChars longest line worth caching — above this a line is unlikely to
     *                 repeat, so an entry would be spent on a single use
     * @param redis    also store entries in Redis, so a line paid for on one instance is
     *                 free on the others and survives a restart
     * @param ttlDays  how long a Redis entry lives
     * @param prewarm  synthesize the fixed lines (disclosure, farewell, "say that
     *                 again") at startup, so the first call of a deploy does not pay
     *                 for them — nor wait for them mid-turn
     */
    public record Cache(
            int size,
            int maxChars,
            boolean redis,
            int ttlDays,
            boolean prewarm
    ) {

        /** Used when the {@code cache} block is absent from the configuration. */
        public static Cache disabled() {
            return new Cache(0, 0, false, 1, false);
        }
    }

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
