package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long a cascade turn waits on the model before treating the request as failed.
 * Bound from {@code voice-agent.dialog.llm-timeout.*}.
 *
 * <p>Without these a stream that stalls — a provider that accepted the request and
 * never sends the first token, or stops mid-sentence — holds the turn, the caller and
 * the concurrency slot for as long as the connection lives. With them the stall becomes
 * an ordinary transient failure: one retry, then the fast model, then the operator.
 *
 * @param tokenGapMs   the longest silence tolerated between two chunks of the model's
 *                     stream, the first token included. 8 s covers a slow first token on a
 *                     loaded provider and is well under what a caller will sit through
 * @param queueDrainMs the longest a turn waits for its queued sentences to finish being
 *                     synthesized and handed to the line after the model has stopped
 *                     writing — a bound on the TTS side of the same turn
 * @param callTimeoutMs the longest one blocking (non-streaming) model call may take — the
 *                     blocking turn, the spoken-line retry, the fact-guard retry. Those have
 *                     no token stream to watch, so without it a hung request held the turn,
 *                     every later caller utterance and the silence watchdog until the ghost
 *                     reaper. 0 = no deadline
 * @param backupAfterMs how long a streamed turn waits for the configured model's first token
 *                     before the same request is also sent to the call's fast model; whichever
 *                     answers first is the one spoken and the other is cancelled (Retell's
 *                     backup model, which kicks in at 1500 ms). A turn already on the fast model,
 *                     or one adopting a speculative reply, is not raced. 0 = off
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.llm-timeout")
public record LlmTimeoutProperties(
        int tokenGapMs,
        int queueDrainMs,
        int callTimeoutMs,
        int backupAfterMs
) {
}
