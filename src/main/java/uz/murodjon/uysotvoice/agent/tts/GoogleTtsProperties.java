package uz.murodjon.uysotvoice.agent.tts;

import java.util.Map;

/**
 * Google Cloud Text-to-Speech settings ({@code voice-agent.tts.google.*}).
 *
 * @param sampleRate   output sample rate (8000 = telephone; matches RTP path)
 * @param speakingRate 0.25–4.0, 1.0 = normal
 * @param pitch        semitone offset, 0.0 = default
 * @param voices       optional per-language voice names (e.g. {@code uz-UZ -> uz-UZ-Standard-A});
 *                     blank/absent lets Google pick a default voice for the language
 */
public record GoogleTtsProperties(
        int sampleRate,
        double speakingRate,
        double pitch,
        Map<String, String> voices
) {
}
