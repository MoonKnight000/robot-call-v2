package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What a company used over a stretch of time, summed from its settled calls.
 *
 * <p>Replaces the per-month counters the billing screens used to read: those were written
 * nowhere and showed the same invented numbers to everyone, while these are the calls the
 * company was actually charged for.
 *
 * @param durationSec connected seconds
 * @param totalTokens prompt and completion tokens together
 * @param ttsChars    characters synthesised
 * @param spendUzs    what all of it was charged at
 */
public record PeriodUsage(long durationSec, long totalTokens, long ttsChars, long spendUzs) {

    public static final PeriodUsage NONE = new PeriodUsage(0, 0, 0, 0);

    /** Whole connected minutes, rounded up — a part-minute call still used a minute of line. */
    public long minutes() {
        return (durationSec + 59) / 60;
    }
}
