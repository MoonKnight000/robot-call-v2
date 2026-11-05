package uz.murodjon.robotcallv2.agent.stt;

/**
 * Settings for ElevenLabs Scribe realtime STT ({@code /v1/speech-to-text/realtime} over WebSocket).
 *
 * @param apiKey                ElevenLabs API key ({@code xi-api-key})
 * @param url                   WebSocket endpoint (default: wss://api.elevenlabs.io/v1/speech-to-text/realtime);
 *                              a data-residency host goes here
 * @param model                 Model id (default: scribe_v2_realtime)
 * @param sampleRate            Sample rate in Hz the audio is sent at — 8000 or 16000, the two rates
 *                              the call pipeline produces (default: 16000)
 * @param connectTimeoutSeconds how long to wait for the WebSocket and its {@code session_started}
 * @param secondaryLanguages    send the call's other expected languages ({@code detect-languages}) as
 *                              {@code secondary_languages}. Off by default: the API reference lists the
 *                              parameter but not how a list is written in the URL, so it is tried on
 *                              purpose, not by surprise
 */
public record ElevenLabsSttProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        int connectTimeoutSeconds,
        boolean secondaryLanguages
) {
    public ElevenLabsSttProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.elevenlabs.io/v1/speech-to-text/realtime";
        }
        if (model == null || model.isBlank()) {
            model = "scribe_v2_realtime";
        }
        if (sampleRate != 8000) {
            sampleRate = 16000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 5;
        }
    }
}
