package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Everything a {@link RealtimeSession} pushes back at the call: the engine's speech,
 * what it heard, what it said, and the tools it wants run.
 *
 * <p>Called from the provider's own network thread. Every method must return quickly —
 * hand slow work (a tool that hits the CRM, a transcript insert) to a virtual thread
 * rather than doing it here, or the engine's next audio frame waits behind it.
 */
public interface RealtimeListener {

    /**
     * The engine's speech, as 16-bit mono PCM at {@link RealtimeProvider#outputSampleRate()},
     * delivered as it is produced. Resample to 8 kHz and play; do not wait for the turn
     * to finish.
     */
    void onBotAudio(short[] pcm);

    /**
     * What the engine heard the caller say. {@code isFinal} marks the settled version of
     * an utterance — the same contract as {@code TranscriptListener} in the cascade
     * pipeline, so transcripts land in {@code call_transcript} the same way.
     */
    void onInputTranscript(String text, boolean isFinal);

    /**
     * What the engine itself said, transcribed after the fact. This is the <em>only</em>
     * text of the bot's own words a realtime call ever sees: the engine speaks straight
     * from audio, so unlike the cascade pipeline there is no sentence to inspect before
     * the caller hears it. Guardrail checks on this text are therefore an audit, not a
     * gate (§4.4).
     */
    void onOutputTranscript(String text);

    /**
     * The caller spoke over the engine and the engine stopped. Whatever audio is already
     * queued for playback is now stale — drop it, or the caller hears the tail of a
     * sentence the engine has abandoned.
     */
    void onInterrupted();

    /** The engine finished speaking and is waiting for the caller. */
    void onTurnComplete();

    /**
     * The engine wants a tool run. Execute it off this thread and answer with
     * {@link RealtimeSession#sendToolResult} using the same {@code callId}.
     *
     * @param callId        opaque id to echo back with the result
     * @param name          the tool's declared name
     * @param argumentsJson the arguments as a JSON object, matching the schema the tool
     *                      was declared with
     */
    void onToolCall(String callId, String name, String argumentsJson);

    /**
     * The conversation ended — the engine closed the connection, or it failed.
     *
     * @param cause why it ended, or {@code null} for a clean close
     */
    void onClosed(Throwable cause);

    /**
     * What one engine response cost, as the vendor reports it. Called once per response
     * (a turn, or the part of one before a tool call) — never with running totals, so the
     * receiver may simply add.
     *
     * <p>A default because not every engine reports usage; one that does not leaves the
     * call's token columns at zero, which is the honest reading.
     *
     * @param promptTokens     input tokens, conversation history and audio included
     * @param completionTokens tokens the engine produced, audio included
     * @param cachedTokens     of {@code promptTokens}, how many the vendor served cached
     */
    default void onUsage(long promptTokens, long completionTokens, long cachedTokens) {
    }

    /**
     * A final caller transcript the engine itself is unsure of (mean token probability
     * below its threshold). Engines without a confidence signal never call this.
     */
    default void onLowConfidenceInput(String text) {
    }

    /**
     * The engine lost its connection and continued the same conversation on a new one
     * (Gemini Live session resumption). Nothing for the call to do — the caller heard at
     * most a short gap — but worth counting: a rising rate is the vendor degrading.
     */
    default void onReconnected() {
    }

    /**
     * Whether the engine is still working on an answer — reasoning or waiting on a tool —
     * after its turn has completed (Gemini 3.8 Live {@code interactionStatus}). While it
     * is, a quiet line is the caller waiting, not the caller gone. Engines that finish
     * with the turn never call this.
     */
    default void onEngineWorking(boolean working) {
    }

    /**
     * A piece of the engine's answer as text, delivered as it is produced — before the
     * whole turn settles into {@link #onOutputTranscript}. Only engines that say so
     * ({@link RealtimeProvider#streamsOutputTranscript()}) call it; a HALF_CASCADE call
     * speaks these pieces with our own TTS instead of playing the engine's audio.
     */
    default void onOutputTranscriptDelta(String delta) {
    }
}
