package uz.murodjon.uysotvoice.agent.realtime;

/**
 * One live conversation with a {@link RealtimeProvider} — the handle the call pipeline
 * pushes the caller's audio into and closes when the call ends.
 *
 * <p>Everything the engine produces comes back through the {@link RealtimeListener} the
 * session was opened with, not from these methods: audio, transcripts and tool calls are
 * all pushed, because the engine speaks when it decides to, not when it is asked.
 *
 * <p>Implementations are used from the RTP consumer thread ({@link #sendAudio}) and from
 * call-executor threads (everything else), so they have to be safe for that — and
 * {@code sendAudio} must never block on the network (PROJECT.md §8.3).
 */
public interface RealtimeSession {

    /**
     * Hand the engine the next slice of the caller's audio, as 16-bit mono PCM at
     * {@link RealtimeProvider#inputSampleRate()}. Called from the RTP consumer thread
     * every 20 ms: queue and return, never block.
     */
    void sendAudio(short[] pcm);

    /**
     * Put {@code text} into the conversation as if the caller had said it, and hand the
     * turn to the engine.
     *
     * <p>What opens a call. A realtime engine listens until it hears something and then
     * answers — left alone on a freshly answered line it stays silent, waiting for a
     * caller who is waiting for it. This is the same synthetic opening the cascade
     * pipeline uses to get its first line ({@code DialogEngine}'s greeting bootstrap);
     * the caller never hears the text, only the answer to it.
     */
    void sendUserText(String text);

    /**
     * Answer a tool call the engine made ({@link RealtimeListener#onToolCall}). The
     * engine resumes speaking once it has the result, so a slow tool is heard as a
     * silence — run it off the pipeline's threads and reply as soon as it returns.
     *
     * @param callId the id from the {@code onToolCall} that is being answered
     * @param result what the tool returned, as the engine should see it
     */
    void sendToolResult(String callId, String result);

    /**
     * End the conversation and release the connection. Safe to call more than once —
     * teardown can reach it from both the hangup path and the engine's own close.
     */
    void close();
}
