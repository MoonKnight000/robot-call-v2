package uz.murodjon.robotcallv2.agent.stt;

import java.util.List;

/**
 * Streaming speech-to-text provider. Abstracted so the Russian stream can later
 * move to Yandex while Uzbek stays on Google (PROJECT.md §2.4).
 */
public interface SttProvider {

    /**
     * Stable id used to select this provider before a run via
     * {@code voice-agent.stt.provider} (e.g. {@code google}, {@code yandex}).
     */
    String name();

    /**
     * Open a streaming recognition session for the given BCP-47 language
     * (e.g. {@code uz-UZ}, {@code ru-RU}). Transcripts are delivered to
     * {@code listener}.
     *
     * @param alternativeLanguages other languages the caller may turn out to speak
     *                            ({@code voice-agent.stt.detect-languages}). One campaign
     *                            dials Uzbek and Russian clients alike, and which of them
     *                            is on the line is only certain once they answer — a
     *                            recognizer pinned to the wrong one returns
     *                            plausible-looking nonsense rather than nothing. Empty
     *                            means recognize {@code languageCode} only; a provider
     *                            that cannot detect the language ignores the list
     * @param externalEndpointing whether the caller decides when an utterance ends and
     *                            says so via {@link SttSession#endUtterance()}
     *                            ({@link EndpointingProperties}). A provider that cannot
     *                            hand that decision over keeps its own detector and
     *                            simply ignores the later signals
     */
    SttSession startStream(String languageCode, List<String> alternativeLanguages,
                           TranscriptListener listener, boolean externalEndpointing);

    /** Recognize {@code languageCode} only — no other language is expected on this call. */
    default SttSession startStream(String languageCode, TranscriptListener listener,
                                   boolean externalEndpointing) {
        return startStream(languageCode, List.of(), listener, externalEndpointing);
    }

    /**
     * Sample rate this provider expects audio in, in Hz. The call pipeline decodes
     * telephone audio at 8 kHz and resamples only when the active provider asks for
     * 16 kHz, so the rate has to come from the provider that is actually running —
     * reading another provider's setting silently sends audio at the wrong rate.
     */
    int sampleRate();
}
