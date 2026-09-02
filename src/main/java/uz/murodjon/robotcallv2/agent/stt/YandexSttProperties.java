package uz.murodjon.robotcallv2.agent.stt;

/**
 * Yandex SpeechKit STT v3 streaming (gRPC, bidirectional). Real-time recognition
 * with server-side endpointing (EOU) — no client-side utterance buffering. v3
 * supports Uzbek ({@code uz-UZ}) as well as {@code ru-RU}.
 *
 * @param apiKey            SpeechKit API key (gRPC metadata {@code authorization: Api-Key ...})
 * @param folderId          Yandex Cloud folder id (sent as {@code x-folder-id}; optional with an API key)
 * @param host              gRPC endpoint host
 * @param port              gRPC endpoint port (443, TLS)
 * @param sampleRate        raw LINEAR16_PCM input rate (8000 telephone)
 * @param model             recognition model ({@code general} by default; blank = server default)
 * @param autoDetectModel   model used instead of {@code model} when a call may be spoken
 *                          in more than one language ({@code voice-agent.stt.detect-languages}).
 *                          SpeechKit only detects the language per sentence under its
 *                          detection model; the ordinary one transcribes whatever it
 *                          hears in the language it was told to expect
 * @param interimResults    deliver partial (interim) hypotheses in addition to finals
 * @param eouSensitivity    how eagerly the server declares the utterance over; see
 *                          {@link EouSensitivity}. The turn cannot start before the final
 *                          arrives, so this is the biggest single lever on turnaround
 * @param eouMaxPauseHintMs hint for the longest pause that is still mid-utterance. A
 *                          caller who lists numbers slowly ("bir … ikki … uch") needs a
 *                          larger value than one who runs sentences together. 0 = no
 *                          hint, let SpeechKit decide
 * @param keepAliveSeconds  how often to ping an idle gRPC connection so the far side
 *                          does not drop it; 0 = no keepalive
 */
public record YandexSttProperties(
        String apiKey,
        String folderId,
        String host,
        int port,
        int sampleRate,
        String model,
        String autoDetectModel,
        boolean interimResults,
        EouSensitivity eouSensitivity,
        int eouMaxPauseHintMs,
        int keepAliveSeconds
) {
}
