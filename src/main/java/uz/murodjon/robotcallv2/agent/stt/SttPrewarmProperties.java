package uz.murodjon.robotcallv2.agent.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Opening the recognizer's stream while the line is still ringing ({@code voice-agent.stt.prewarm.*}).
 *
 * @param enabled    open a stream for an outbound call when it is originated, so a caller who
 *                   answers is heard by a stream that is already up. Off by default: an
 *                   experiment that buys the stream set-up time (judge it by the first
 *                   {@code voice.stt.final.latency} of a call) at the price of a vendor session
 *                   for every call that rings, answered or not
 * @param maxPending the most streams held open for calls that have not been answered yet; a
 *                   call over the cap simply opens its stream at answer time as usual. Keeps a
 *                   wide campaign from spending the vendor's concurrency limit on dial tones
 * @param ttlSlackMs how long past the call's ring time an unclaimed stream is kept before it
 *                   is closed (the call was never answered)
 */
@ConfigurationProperties(prefix = "voice-agent.stt.prewarm")
public record SttPrewarmProperties(
        boolean enabled,
        int maxPending,
        long ttlSlackMs
) {
}
