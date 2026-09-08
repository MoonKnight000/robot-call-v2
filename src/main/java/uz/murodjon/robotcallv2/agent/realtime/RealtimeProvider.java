package uz.murodjon.robotcallv2.agent.realtime;

/**
 * A speech-to-speech engine: audio in, audio out, with recognition, reasoning and
 * speech all inside the vendor (PROJECT.md §2.4/§2.5, {@code PipelineMode.REALTIME}).
 *
 * <p>Deliberately <strong>not</strong> an {@code SttProvider} plus a {@code TtsProvider}.
 * Those two meet at text — the pipeline decides when an utterance ended, what the model
 * should answer, and when to speak it. A realtime engine owns all three decisions,
 * including endpointing and barge-in, so there is no text boundary to hang the existing
 * interfaces on. It gets its own family here for the same reason.
 *
 * <p>Every implementation is registered by {@link RealtimeProviderRegistry}; which one a
 * call uses comes from that call's agent ({@code ai_agent.realtime_provider}).
 */
public interface RealtimeProvider {

    /** Stable id used to select this engine (e.g. {@code gemini-live}). */
    String name();

    /** Whether this engine can hold a conversation in the given BCP-47 language (e.g. {@code uz-UZ}). */
    boolean supports(String language);

    /**
     * Sample rate this engine expects the caller's audio in, in Hz. The call pipeline
     * decodes telephone audio at 8 kHz and resamples to whatever the engine that is
     * actually running asks for — reading another engine's setting silently sends audio
     * at the wrong rate.
     */
    int inputSampleRate();

    /**
     * Sample rate this engine's own speech arrives at, in Hz — resampled down to the
     * 8 kHz the RTP leg carries before it reaches {@code RtpEndpoint.playPcm}. Usually
     * higher than {@link #inputSampleRate()}: engines listen at telephone quality and
     * answer at broadcast quality.
     */
    int outputSampleRate();

    /**
     * The model id this engine will actually put on the wire for {@code config} — the
     * call's own model where it named one, this engine's configured default otherwise
     * ({@link RealtimeCallConfig#modelOr}).
     *
     * <p>Asked rather than guessed because only the provider knows what it falls back to.
     * The dialog engine records the answer on the call's technical detail (§10.5), where a
     * REALTIME call would otherwise be filed under the cascade pipeline's chat model.
     */
    String resolveModel(RealtimeCallConfig config);

    /**
     * Open a conversation. The returned session is live from this point: the engine may
     * start speaking (a greeting) before any audio has been sent to it.
     *
     * @param config   what this call is — language, instructions, voice, and the tools
     *                 the engine may call
     * @param listener where the engine's audio, transcripts and tool calls are delivered
     * @throws uz.murodjon.robotcallv2.shared.exception.ExternalServiceException if the
     *         engine cannot be reached or refuses the session
     */
    RealtimeSession startSession(RealtimeCallConfig config, RealtimeListener listener);
}
