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
 *                          arrives, so this is the biggest single lever on turnaround.
 *                          {@code DEFAULT}, not {@code HIGH}: {@code HIGH} ends the
 *                          utterance at the first opportunity, and paired with a 500 ms
 *                          pause hint it cut callers off mid-sentence
 * @param eouMaxPauseHintMs hint for the longest pause that is still mid-utterance. A
 *                          caller who lists numbers slowly ("bir … ikki … uch") needs a
 *                          larger value than one who runs sentences together. 0 = no
 *                          hint, let SpeechKit decide.
 *                          <p><b>Not read on the default path.</b> {@code stt.vad-gating}
 *                          + {@code stt.endpointing} switch SpeechKit to the external
 *                          classifier, where this side says when the utterance ended and
 *                          {@code vad-gating.post-roll-ms} is the budget. This value is
 *                          what a call falls back to when gating is turned off — no VAD
 *                          model, or a gate that turns out to cut callers off.
 *                          <p>900. It was 500, which does not survive real speech: on a
 *                          recorded call it ended the turn on a 0.60 s pause and again on
 *                          a 0.78 s one, both times mid-sentence. The second cost the call
 *                          — "keyingi oy" was finalized, the bot answered and hung up, and
 *                          "to'layman oktyabrda" arrived 0.2 s later with nobody left to
 *                          hear it. It was then 1200, which cleared both with room to
 *                          spare and put 1.2–2.3 s in front of every reply. 900 clears the
 *                          0.78 s pause that actually broke a call and gives back 300 ms
 *                          of margin nothing was using; speculation pays for what is left.
 *                          <p>SpeechKit accepts [500, 5000] and nothing else: below 500 it
 *                          does not clamp or ignore the hint, it kills the session with
 *                          {@code INVALID_ARGUMENT} on every call
 * @param keepAliveSeconds  how often to ping an idle gRPC connection so the far side
 *                          does not drop it; 0 = no keepalive
 * @param textNormalization turn spoken numbers, dates and times into digits ("bir million
 *                          besh yuz ming" → "1500000"). The model reads an amount far more
 *                          reliably as digits, and so does the fact guard that checks it
 * @param literatureText    punctuate the transcript. Without it "A?" and "A" arrive as the
 *                          same letter, and whether the caller asked or answered is the
 *                          first thing the model needs to know
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
        int keepAliveSeconds,
        boolean textNormalization,
        boolean literatureText
) {
}
