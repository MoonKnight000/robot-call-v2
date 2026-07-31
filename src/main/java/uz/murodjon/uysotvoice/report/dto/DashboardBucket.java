package uz.murodjon.uysotvoice.report.dto;

import java.time.Instant;

/**
 * One time bucket of {@link DashboardRange#granularity()} size, aggregated from
 * {@code call_attempt}. Shared by two screens (§10.2 UI-DESIGN.md): the KPI cards read
 * it as four per-metric sparklines, and the "Qo'ng'iroqlar dinamikasi" chart reads
 * {@code answered}/{@code noAnswer}/{@code error} as its three stacked-area series —
 * one query, two shapes, rather than near-duplicate SQL for each.
 *
 * @param answered  connected calls (had a measured duration)
 * @param noAnswer  attempts that ended {@code NO_ANSWER}
 * @param error     attempts that ended {@code FAILED}
 */
public record DashboardBucket(
        Instant bucketStart,
        long total,
        long answered,
        long noAnswer,
        long error,
        Double avgDurationSec,
        long promises
) {
}
