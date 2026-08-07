package uz.murodjon.uysotvoice.agent.stt;

/**
 * Streaming speech-to-text provider. Abstracted so the Russian stream can later
 * move to Yandex while Uzbek stays on Google (PROJECT.md §2.4).
 */
public interface SttProvider {

    /**
     * Open a streaming recognition session for the given BCP-47 language
     * (e.g. {@code uz-UZ}, {@code ru-RU}). Transcripts are delivered to
     * {@code listener}.
     *
     * @param externalEndpointing whether the caller decides when an utterance ends and
     *                            says so via {@link SttSession#endUtterance()}
     *                            ({@link EndpointingProperties}). A provider that cannot
     *                            hand that decision over keeps its own detector and
     *                            simply ignores the later signals
     */
    SttSession startStream(String languageCode, TranscriptListener listener, boolean externalEndpointing);

    /**
     * Sample rate this provider expects audio in, in Hz. The call pipeline decodes
     * telephone audio at 8 kHz and resamples only when the active provider asks for
     * 16 kHz, so the rate has to come from the provider that is actually running —
     * reading another provider's setting silently sends audio at the wrong rate.
     */
    int sampleRate();
}
