package uz.murodjon.robotcallv2.agent.tts;

import java.util.List;
import java.util.Map;

/**
 * Yandex SpeechKit TTS v3 streaming (gRPC {@code Synthesizer.UtteranceSynthesis}),
 * settings under {@code voice-agent.tts.yandex.*}.
 *
 * @param apiKey     SpeechKit API key (gRPC metadata {@code authorization: Api-Key ...})
 * @param folderId   optional Yandex Cloud folder id (sent as {@code x-folder-id})
 * @param voice      default voice name (ru-RU: alena, filipp, ermil, jane, omazh, zahar, ...)
 * @param voices     per-language voice overrides (e.g. {@code uz-UZ -> nigora}); a
 *                   language absent here falls back to {@code voice}. Each language
 *                   needs its own voice — a ru-RU voice cannot speak Uzbek text.
 * @param voiceRoles roles ("amplua") each voice accepts, keyed by voice name. A role
 *                   belongs to a single voice: asking a voice for one it does not have
 *                   fails the whole synthesis ("role neutral is not supported for voice
 *                   nigora"), so a voice listed with an empty list — or not listed at
 *                   all — is always spoken without a role
 * @param host       gRPC endpoint host
 * @param port       gRPC endpoint port (443, TLS)
 * @param sampleRate raw LINEAR16_PCM output rate (8000 for telephone)
 * @param keepAliveSeconds how often to ping an idle gRPC connection so the far side does
 *                   not drop it. Synthesis is bursty — a call can go a minute between
 *                   requests — and a reconnect costs a TCP+TLS handshake inside a live
 *                   turn, exactly where the §1.3 budget cannot absorb it. 0 = no keepalive
 */
public record YandexTtsProperties(
        String apiKey,
        String folderId,
        String voice,
        Map<String, String> voices,
        Map<String, List<String>> voiceRoles,
        String host,
        int port,
        int sampleRate,
        int keepAliveSeconds
) {
}
