package uz.murodjon.uysotvoice.agent.tts;

import java.util.Map;

/**
 * Aisha realtime text-to-speech (WebSocket), settings under
 * {@code voice-agent.tts.aisha.*}.
 *
 * <p>Aisha names a speaking mood rather than a person: the Uzbek model (Gulnoza) speaks
 * as {@code neutral}, {@code cheerful}, {@code happy} or {@code sad}, and that mood is
 * what the API calls {@code speaker_id}. A campaign voice from the {@code tts_voice}
 * catalog owned by this provider therefore carries the mood in its name column.
 *
 * <p>The output format is fixed by Aisha at 16 kHz mono WAV, so nothing here selects it —
 * the provider parses that container and downsamples to the 8 kHz the RTP path needs.
 *
 * @param apiKey           Aisha API key (sent as the {@code token} query parameter)
 * @param url              realtime WebSocket endpoint; {@code token} is appended by the provider
 * @param speaker          mood used when neither the call nor {@code speakers} names one
 * @param speakers         per-language mood overrides (e.g. {@code uz-UZ -> neutral})
 * @param requestTimeoutMs how long one line may take before the turn gives up on it. A
 *                         synthesis that never answers would otherwise hold the call
 *                         silent for as long as the caller stays on the line
 */
public record AishaTtsProperties(
        String apiKey,
        String url,
        String speaker,
        Map<String, String> speakers,
        long requestTimeoutMs
) {
}
