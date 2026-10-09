package uz.murodjon.robotcallv2.promise.domain.entity;

import java.math.BigDecimal;

/**
 * Promises whose date falls in a range, by how they ended.
 *
 * @param keptRate        kept among the resolved ones (kept + broken), in percent; null when none is resolved
 * @param promisedAmount  the sum named across all of them (promises without an amount add nothing)
 * @param keptAmount      the sum named across the kept ones
 */
public record PaymentPromiseStats(
        long total,
        long open,
        long kept,
        long broken,
        long cancelled,
        Double keptRate,
        BigDecimal promisedAmount,
        BigDecimal keptAmount
) {

    /** The counts as read; the kept rate is worked out here. */
    public static PaymentPromiseStats of(long total, long open, long kept, long broken, long cancelled,
                                         BigDecimal promisedAmount, BigDecimal keptAmount) {
        long resolved = kept + broken;
        Double keptRate = resolved == 0 ? null : kept * 100.0 / resolved;
        return new PaymentPromiseStats(total, open, kept, broken, cancelled, keptRate, promisedAmount, keptAmount);
    }
}
