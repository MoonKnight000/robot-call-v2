package uz.murodjon.uysotvoice.agent.tts;

/**
 * Text-to-speech provider (PROJECT.md §2.5). TTS is stateless — text in, audio
 * out — so a single blocking call is enough; the paced 20ms streaming to the
 * peer happens in {@code RtpEndpoint.playPcm}. Providers are language-scoped and
 * selected by {@link TtsRouter}.
 */
public interface TtsProvider {

    /** Stable id for logging (e.g. {@code google}, {@code yandex}). */
    String name();

    /** Whether this provider can synthesize the given BCP-47 language (e.g. {@code uz-UZ}). */
    boolean supports(String language);

    /**
     * Synthesize {@code text} into telephone-grade audio.
     *
     * @return 8 kHz mono 16-bit PCM samples, ready for {@code RtpEndpoint.playPcm}
     * @throws RuntimeException if synthesis fails or the provider is unavailable
     */
    short[] synthesize(String text, String language);
}
