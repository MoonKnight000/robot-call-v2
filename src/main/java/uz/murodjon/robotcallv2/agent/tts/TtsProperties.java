package uz.murodjon.robotcallv2.agent.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Text-to-speech settings. Bound from {@code voice-agent.tts.*} (PROJECT.md §12).
 *
 * <p>The selectable voice catalog itself is no longer here — it moved to the
 * {@code tts_voice} table (PROJECT.md §2.5), read via {@code voice.service.TtsVoiceService}.
 *
 * @param enabled         master switch; when false no TTS is synthesized
 * @param provider        active provider id ({@code gemini}, {@code yandex}, {@code aisha}, {@code cartesia})
 * @param defaultLanguage BCP-47 language used when a call/turn does not specify one
 * @param cache           synthesized-audio cache settings
 * @param gemini          Gemini TTS settings (gemini-3.1-flash-tts-preview)
 * @param google          Google TTS settings (legacy)
 * @param yandex          Yandex SpeechKit settings
 * @param aisha           Aisha settings
 * @param cartesia        Cartesia Sonic ultra-low latency TTS settings
 * @param openAi          OpenAI speech settings (gpt-4o-mini-tts)
 */
@ConfigurationProperties(prefix = "voice-agent.tts")
public record TtsProperties(
        boolean enabled,
        String provider,
        String defaultLanguage,
        TtsCacheProperties cache,
        GeminiTtsProperties gemini,
        GoogleTtsProperties google,
        YandexTtsProperties yandex,
        AishaTtsProperties aisha,
        CartesiaTtsProperties cartesia,
        OpenAiTtsProperties openAi
) {
}
