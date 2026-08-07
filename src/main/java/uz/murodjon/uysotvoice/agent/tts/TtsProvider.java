package uz.murodjon.uysotvoice.agent.tts;

import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

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
     * Synthesize {@code text} into telephone-grade audio with the provider's
     * configured voice for {@code language}.
     *
     * @return 8 kHz mono 16-bit PCM samples, ready for {@code RtpEndpoint.playPcm}
     * @throws RuntimeException if synthesis fails or the provider is unavailable
     */
    default short[] synthesize(String text, String language) {
        return synthesize(text, language, null);
    }

    /**
     * Synthesize {@code text} with an explicit provider-side voice name — what a
     * campaign's chosen voice resolves to (§2.5).
     *
     * @param voice provider-side voice name; {@code null}/blank uses the configured
     *              voice for {@code language}
     */
    short[] synthesize(String text, String language, String voice);

    /**
     * As {@link #synthesize(String, String, String)}, with a company's TTS overrides
     * (§11 settings/voice) layered over the provider's own configured defaults. The
     * default implementation ignores {@code style} — only providers that support
     * dynamic speed/pitch need to override it.
     */
    default short[] synthesize(String text, String language, String voice, EffectiveVoiceSettings style) {
        return synthesize(text, language, voice);
    }

    /**
     * As {@link #synthesize(String, String, String, EffectiveVoiceSettings)}, but delivers
     * PCM to {@code onChunk} as it becomes available instead of returning the whole
     * utterance at once. Providers whose wire protocol streams audio (Yandex v3 gRPC)
     * override this to start playback before the full sentence has finished synthesizing;
     * the default just wraps the blocking call and delivers it as a single chunk.
     *
     * @throws RuntimeException if synthesis fails or the provider is unavailable — any
     *                          chunks already delivered to {@code onChunk} are still valid
     */
    default void synthesizeStreaming(String text, String language, String voice, EffectiveVoiceSettings style,
                                      PcmChunkListener onChunk) {
        onChunk.onChunk(synthesize(text, language, voice, style));
    }
}
