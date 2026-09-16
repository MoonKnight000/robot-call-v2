package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timings and thresholds of the dialog loop that used to be constants (NEXT-PLAN V.6).
 * Bound from {@code voice-agent.dialog.tuning.*}; the defaults in {@code dialog.yml} are
 * the values the constants had.
 *
 * @param drainTimeoutMs    longest a closing call waits for its queued audio to finish
 *                          playing before it hangs up anyway, on both pipelines
 * @param farewellGraceMs   how long a realtime engine has to say goodbye before the line
 *                          is dropped — a farewell is one sentence
 * @param minEchoChars      shortest caller transcript that may be dismissed as the bot's
 *                          own echo; anything shorter is a caller reading a figure back or
 *                          agreeing in the bot's own words, and is never thrown away
 * @param llmRetryBackoffMs pause before a failed model request is retried — long enough
 *                          for a provider shedding load to recover, short enough to stay
 *                          inside the silence the filler covers
 * @param maxStageAttempts  turns a scripted call may spend in one stage before it is taken
 *                          as a loop and handed to an operator
 * @param playbackPollMs    how often a closing call checks whether its queued audio has
 *                          finished playing; 0 means 100
 * @param hangupTailMs      pause after the queue has drained and before the hangup (or
 *                          transfer), on both pipelines: the last frames are still on their
 *                          way through Asterisk to the caller when the queue reads empty, and
 *                          hanging up at once clipped the end of the goodbye. Runs on a
 *                          virtual worker thread, so the wait holds no platform thread
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.tuning")
public record DialogTuningProperties(
        int drainTimeoutMs,
        int farewellGraceMs,
        int minEchoChars,
        int llmRetryBackoffMs,
        int maxStageAttempts,
        int playbackPollMs,
        int hangupTailMs
) {

    public DialogTuningProperties {
        // A zero poll would spin a thread flat out for the length of every goodbye.
        if (playbackPollMs <= 0) {
            playbackPollMs = 100;
        }
        if (hangupTailMs < 0) {
            hangupTailMs = 0;
        }
    }
}
