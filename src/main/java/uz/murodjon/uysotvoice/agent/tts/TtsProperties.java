package uz.murodjon.uysotvoice.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Text-to-speech settings. Bound from {@code voice-agent.tts.*} (PROJECT.md §12).
 *
 * <p>The selectable voice catalog itself is no longer here — it moved to the
 * {@code tts_voice} table (PROJECT.md §2.5), read via {@code voice.service.TtsVoiceService}.
 *
 * @param enabled         master switch; when false no TTS is synthesized
 * @param provider        preferred provider id ({@code google}/{@code yandex}); used
 *                        first when it supports the language, else the router falls
 *                        back by language support
 * @param defaultLanguage BCP-47 language used when a call/turn does not specify one
 * @param cache           synthesized-audio cache settings; a hit removes both a
 *                        synthesis round trip from a live turn and a per-character
 *                        charge from the bill
 * @param google          Google TTS settings (uz-UZ and ru-RU)
 * @param yandex          Yandex SpeechKit settings (uz-UZ via the Nigora voice, ru-RU)
 */
@ConfigurationProperties(prefix = "voice-agent.tts")
public record TtsProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        TtsCacheProperties cache,
        GoogleTtsProperties google,
        YandexTtsProperties yandex
) {
}
