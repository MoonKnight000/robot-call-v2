package uz.murodjon.uysotvoice.report.dto;

/**
 * Scalar aggregates for one time window (§10.2 dashboard KPI cards): how many calls
 * started in it, how many were actually answered, their average duration, and how
 * many ended in a payment promise.
 *
 * @param totalCalls    call attempts started in the window
 * @param answeredCalls attempts that connected (had a measured duration)
 * @param avgDurationSec average duration of the answered ones, or null if none did
 * @param promises      attempts that ended {@code PROMISE_TO_PAY}
 */
public record DashboardTotals(long totalCalls, long answeredCalls, Double avgDurationSec, long promises) {
}
