package uz.murodjon.uysotvoice.agent.realtime;

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
}
