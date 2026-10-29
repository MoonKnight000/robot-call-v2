package uz.murodjon.robotcallv2.agent.realtime;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

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
     * Add {@code text} to the conversation without handing the turn to the engine: it
     * shapes the engine's next answer instead of getting one of its own. For guidance the
     * caller must not hear answered — a supervisor's whisper.
     *
     * <p>Falls back to {@link #sendUserText} for an engine that has no way to add context
     * quietly; that engine answers it straight away.
     */
    default void sendContext(String text) {
        sendUserText(text);
    }

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
     * Tell the engine how much of the reply it was cut off in actually reached the caller.
     *
     * <p>An engine generates speech faster than it is played, so when the caller barges in
     * its own history holds the whole reply — including the part nobody heard, which it
     * will later refer back to ("as I said…"). Engines that keep that history on the
     * server and accept a correction (OpenAI's {@code conversation.item.truncate}) are told
     * here; those that do not, need not do anything.
     *
     * @param heardMs milliseconds of the interrupted reply that were played to the caller
     */
    default void truncatePlayback(long heardMs) {
    }

    /**
     * Replace the engine's instructions and tools mid-call — what an AGENT_TRANSFER stage of
     * a flow needs, since the call goes on under another agent's script. The conversation so
     * far is kept.
     *
     * @return whether the engine took them; false for an engine that fixes both when the
     *         session opens (Gemini Live's setup message), which is the default
     */
    default boolean updateConfiguration(String systemPrompt, List<ToolCallback> tools) {
        return false;
    }

    /**
     * End the conversation and release the connection. Safe to call more than once —
     * teardown can reach it from both the hangup path and the engine's own close.
     */
    void close();
}
