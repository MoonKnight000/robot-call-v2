package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One duration range of the "Suhbat davomiyligi taqsimoti" histogram (§10.10 Grafik 5),
 * over calls that connected ({@code duration_sec} not null). {@code rangeEndSec} is
 * {@code null} for the open-ended last bucket ({@code 600+}).
 */
public record DurationHistogramBucket(String rangeLabel, int rangeStartSec, Integer rangeEndSec, long count) {
}

