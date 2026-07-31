package uz.murodjon.uysotvoice.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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
        TtsCacheProperties cache,
        List<TtsVoice> catalog,
        GoogleTtsProperties google,
        YandexTtsProperties yandex
) {
}
