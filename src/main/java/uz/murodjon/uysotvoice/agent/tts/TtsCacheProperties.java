package uz.murodjon.uysotvoice.agent.tts;

/**
 * Synthesized-audio cache settings ({@code voice-agent.tts.cache.*}).
 *
 * @param size     how many lines to keep in the per-process LRU; {@code 0} disables it
 * @param maxChars longest line worth caching — above this a line is unlikely to
 *                 repeat, so an entry would be spent on a single use
 * @param redis    also store entries in Redis, so a line paid for on one instance is
 *                 free on the others and survives a restart
 * @param ttlDays  how long a Redis entry lives
 * @param prewarm  synthesize the fixed lines (disclosure, farewell, "say that
 *                 again") at startup, so the first call of a deploy does not pay
 *                 for them — nor wait for them mid-turn
 */
public record TtsCacheProperties(
        int size,
        int maxChars,
        boolean redis,
        int ttlDays,
        boolean prewarm
) {

    /** Used when the {@code cache} block is absent from the configuration. */
    public static TtsCacheProperties disabled() {
        return new TtsCacheProperties(0, 0, false, 1, false);
    }
}
