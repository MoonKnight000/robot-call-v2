package uz.murodjon.uysotvoice.agent.tts;

import java.util.Map;

/**
 * Yandex SpeechKit synthesis settings ({@code voice-agent.tts.yandex.*}).
 *
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
public record YandexTtsProperties(
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
