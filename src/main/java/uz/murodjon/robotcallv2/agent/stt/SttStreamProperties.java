package uz.murodjon.robotcallv2.agent.stt;

/**
 * How one call's recognition stream protects itself (NEXT-PLAN V.6) — formerly constants
 * of {@code SttStreamBridge}. Bound from {@code voice-agent.stt.stream.*}; the defaults in
 * {@code speech.yml} are the values the constants had.
 *
 * @param replayBufferSamples   audio kept to replay into a stream reopened after a drop;
 *                              16 000 is two seconds at 8 kHz
 * @param promotedFinalTtlMs    how long after a promoted final the provider's own final for
 *                              the same utterance is still expected, and dropped. Past this
 *                              a final belongs to the next utterance
 * @param failoverAfterFailures failures of one provider — a stream that would not open, or
 *                              one that took audio without ever answering — before the call
 *                              moves to another vendor. Two, not one: a single dropped stream
 *                              is ordinary, and switching vendors mid-sentence costs the
 *                              recognizer whatever context it had built up
 */
public record SttStreamProperties(
        int replayBufferSamples,
        long promotedFinalTtlMs,
        int failoverAfterFailures
) {

    /** The values the constants had — used when no {@code stream} block is bound. */
    public static final SttStreamProperties DEFAULTS = new SttStreamProperties(16_000, 2_500, 2);
}
