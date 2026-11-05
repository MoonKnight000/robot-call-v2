package uz.murodjon.robotcallv2.agent.stt;

import uz.murodjon.robotcallv2.agent.provider.NamedProvider;

import java.util.List;

/**
 * Streaming speech-to-text provider. Abstracted so the Russian stream can later
 * move to Yandex while Uzbek stays on Google (PROJECT.md §2.4).
 */
public interface SttProvider extends NamedProvider {

    /**
     * Whether this provider can recognize the given BCP-47 language at all. Default is yes:
     * only a provider known to lack a language says otherwise, so that a mid-call failover
     * never lands on a recognizer that returns nonsense for it.
     */
    default boolean supports(String language) {
        return true;
    }

    /**
     * Whether a stream of this provider may sit open, with no audio sent, for the seconds a
     * line rings ({@link SttPrewarmRegistry}). Default is no: a provider that closes an idle
     * stream (Yandex does within seconds) would hand the call a dead one.
     */
    default boolean supportsPrewarm() {
        return false;
    }

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

    /**
     * The same, told what this call is likely to contain ({@link SttHints}) — the client's
     * name, the payment services they will be pointed at.
     *
     * <p>Default is to ignore them, because most of these APIs have nowhere to put them:
     * Deepgram's keyterm prompting is Nova-3 English only, and the Yandex and Aisha
     * endpoints used here take no phrase list at all. A provider that <em>can</em> be
     * biased overrides this; nothing else changes for the ones that cannot.
     */
    default SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                   TranscriptListener listener, boolean externalEndpointing,
                                   List<String> hints) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing);
    }

    /**
     * The same, on the recognition model this call asks for ({@code ai_agent.stt_model}).
     *
     * <p>Default is to ignore it and stay on the model the deployment configured, because
     * a provider either has one model or names it in a way nothing else does: what
     * Deepgram calls {@code nova-2} means nothing to Yandex, and Aisha has no model to
     * pick at all. A blank or unknown name is the configured model too — a live call is
     * the wrong place to fail over a settings typo.
     *
     * @param model provider-side model name, or {@code null} for the configured one
     */
    default SttSession startStream(String languageCode, List<String> alternativeLanguages,
                                   TranscriptListener listener, boolean externalEndpointing,
                                   List<String> hints, String model) {
        return startStream(languageCode, alternativeLanguages, listener, externalEndpointing, hints);
    }

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

    /**
     * The longest a single stream may live before the provider ends it, in seconds; 0 = no
     * limit. A call that runs past it gets a new stream in a pause rather than losing one
     * mid-sentence ({@link SttStreamBridge}).
     */
    default int maxStreamSeconds() {
        return 0;
    }
}
