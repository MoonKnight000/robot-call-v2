package uz.murodjon.robotcallv2.agent.stt;

/**
 * Google Cloud Speech-to-Text settings ({@code voice-agent.stt.google.*}).
 *
 * @param model                recognition model ({@code phone_call} for telephony)
 * @param sampleRate           audio sample rate sent to Google (8000 = native telephone)
 * @param enablePunctuation    enable automatic punctuation
 * @param endpointingSilenceMs silence that marks end of an utterance
 */
public record GoogleSttProperties(
        String model,
        int sampleRate,
        boolean enablePunctuation,
        int endpointingSilenceMs
) {
}
