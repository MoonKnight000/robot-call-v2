package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What one finished call actually consumed, before anything is priced.
 *
 * <p>Kept apart from the money so a charge can be recomputed later from the same inputs:
 * a company disputing an invoice line is asking "how did you get this number", and the
 * answer is this record plus the rate version stamped on the charge.
 *
 * @param durationSec       connected seconds; what recognition and the trunk are priced on
 * @param promptTokens      prompt tokens across every turn, cached ones included
 * @param completionTokens  tokens the model wrote
 * @param cachedTokens      of {@code promptTokens}, how many the provider served cached
 * @param ttsChars          characters the agent asked a synthesizer for
 * @param realtimeSeconds   seconds the call ran on a speech-to-speech engine, which is
 *                          priced per minute in place of tokens, recognition and synthesis;
 *                          0 for a cascade call. The tokens are still recorded for such a
 *                          call, but only as information
 */
public record CallUsage(
        int durationSec,
        long promptTokens,
        long completionTokens,
        long cachedTokens,
        long ttsChars,
        int realtimeSeconds
) {
    public static final CallUsage NONE = new CallUsage(0, 0, 0, 0, 0, 0);

    /** A cascade call's usage — nothing ran on a realtime engine. */
    public CallUsage(int durationSec, long promptTokens, long completionTokens, long cachedTokens, long ttsChars) {
        this(durationSec, promptTokens, completionTokens, cachedTokens, ttsChars, 0);
    }

    /** Prompt tokens billed at the full rate — the cached ones are priced separately. */
    public long billablePromptTokens() {
        return Math.max(0, promptTokens - cachedTokens);
    }

    /** Whether the engine's per-minute price replaces the token, recognition and synthesis lines. */
    public boolean realtime() {
        return realtimeSeconds > 0;
    }
}
